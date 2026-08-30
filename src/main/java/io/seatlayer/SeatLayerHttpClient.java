package io.seatlayer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * The transport: auth, idempotency, retry, and error mapping.
 *
 * <p>Built on {@code java.net.http.HttpClient} from the JDK rather than OkHttp or Apache
 * HttpClient. A server SDK that drags in an HTTP stack forces its version on every
 * consumer, and in Java that is a real cost — an application already using a different
 * version has to resolve the conflict before it can use us at all.
 */
final class SeatLayerHttpClient {

    static final String DEFAULT_BASE_URL = "https://api.seatlayer.io";
    static final int DEFAULT_MAX_RETRIES = 3;
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** The API's own charset for Idempotency-Key. */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    /** Injectable so tests can run without a network. */
    interface Transport {
        Response send(String method, String url, Map<String, String> headers, String body);
    }

    record Response(int status, String body, Map<String, String> headers) {
    }

    private record BinaryBody(byte[] bytes, String contentType) {
    }

    private final String secretKey;
    private final String baseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final Transport transport;
    private final String mode;

    SeatLayerHttpClient(
            String secretKey, String baseUrl, int maxRetries, Duration timeout, Transport transport) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("A SeatLayer secret key is required.");
        }
        // Caught here rather than as a 401 three round-trips later. The pk_ case gets
        // its own message: it is the one people paste by mistake.
        if (secretKey.startsWith("pk_")) {
            throw new IllegalArgumentException(
                    "That is a publishable key. The server SDK needs a secret key (sk_live_… or sk_test_…).");
        }
        if (!secretKey.startsWith("sk_")) {
            throw new IllegalArgumentException("A SeatLayer secret key starts with sk_live_ or sk_test_.");
        }

        this.secretKey = secretKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.transport = transport != null ? transport : new JdkTransport(timeout);
        this.mode = secretKey.startsWith("sk_test_")
                ? "test"
                : secretKey.startsWith("sk_live_") ? "live" : "unknown";
    }

    String mode() {
        return mode;
    }

    static void assertValidIdempotencyKey(String key) {
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Idempotency-Key \"" + key + "\": allowed characters are "
                            + "A-Z a-z 0-9 . _ : - and the length must be 1-128.");
        }
    }

    /** Percent-encode a path segment, including slashes. */
    static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    Map<String, Object> request(
            String method,
            String path,
            Map<String, Object> query,
            Map<String, Object> body) {
        return performRequest(method, path, query, body, false, null);
    }

    private Map<String, Object> performRequest(
            String method,
            String path,
            Map<String, Object> query,
            Object body,
            boolean headerReplay,
            String idempotencyKey) {

        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (query != null && !query.isEmpty()) {
            StringBuilder queryString = new StringBuilder();
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                queryString.append(queryString.isEmpty() ? '?' : '&')
                        .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
            url.append(queryString);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + secretKey);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "seatlayer-java");

        String payload = null;
        if (body instanceof BinaryBody binary) {
            payload = new String(binary.bytes(), StandardCharsets.ISO_8859_1);
            headers.put("Content-Type", binary.contentType());
        } else if (body != null) {
            payload = Json.write(body);
            headers.put("Content-Type", "application/json");
        }

        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        if (!read && (headerReplay || idempotencyKey != null)) {
            String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
            assertValidIdempotencyKey(key);
            headers.put("Idempotency-Key", key);
        }

        int attemptLimit = read || headerReplay ? maxRetries : 1;
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < attemptLimit; attempt++) {
            Response response;
            try {
                response = transport.send(method, url.toString(), headers, payload);
            } catch (SeatLayerConnectionException error) {
                lastError = error;
                if (attempt < attemptLimit - 1) {
                    sleep(backoffSeconds(attempt, null));
                    continue;
                }
                throw error;
            }

            if (response.status() >= 200 && response.status() < 300) {
                if (response.status() == 204 || response.body() == null || response.body().isBlank()) {
                    return Map.of();
                }
                Object decoded = Json.read(response.body());
                return decoded instanceof Map<?, ?> ? Json.readObject(response.body()) : Map.of("data", decoded);
            }

            Map<String, Object> errorBody;
            try {
                errorBody = Json.readObject(response.body() == null ? "{}" : response.body());
            } catch (RuntimeException ignored) {
                errorBody = Map.of();
            }

            double retryAfter = parseRetryAfter(response.headers(), errorBody);

            if (isRetryableStatus(response.status()) && attempt < attemptLimit - 1) {
                sleep(backoffSeconds(attempt, response.status() == 429 ? retryAfter : null));
                continue;
            }

            throw SeatLayerException.fromResponse(
                    response.status(), errorBody, response.headers().get("x-request-id"), retryAfter);
        }

        throw lastError != null
                ? lastError
                : new SeatLayerConnectionException("Request failed with no attempts made.", null);
    }

    Map<String, Object> get(String path) {
        return performRequest("GET", path, null, null, false, null);
    }

    Map<String, Object> get(String path, Map<String, Object> query) {
        return performRequest("GET", path, query, null, false, null);
    }

    Map<String, Object> post(String path) {
        return performRequest("POST", path, null, null, false, null);
    }

    Map<String, Object> post(String path, Map<String, Object> body) {
        return performRequest("POST", path, null, body, false, null);
    }

    Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        return performRequest("POST", path, null, body, false, idempotencyKey);
    }

    Map<String, Object> postWithHeaderReplay(String path) {
        return performRequest("POST", path, null, null, true, null);
    }

    Map<String, Object> postWithHeaderReplay(String path, Map<String, Object> body) {
        return performRequest("POST", path, null, body, true, null);
    }

    Map<String, Object> postWithHeaderReplay(
            String path, Map<String, Object> body, String idempotencyKey) {
        return performRequest("POST", path, null, body, true, idempotencyKey);
    }

    Map<String, Object> mutationWithHeaderReplay(
            String method, String path, Map<String, Object> body, String idempotencyKey) {
        return performRequest(method, path, null, body, true, idempotencyKey);
    }

    Map<String, Object> put(String path, Map<String, Object> body) {
        return performRequest("PUT", path, null, body, false, null);
    }

    Map<String, Object> putBinary(String path, byte[] bytes, String contentType) {
        if (!java.util.Set.of("image/png", "image/jpeg", "image/webp", "application/octet-stream")
                .contains(contentType)) {
            throw new IllegalArgumentException("Unsupported poster content type: " + contentType);
        }
        return performRequest("PUT", path, null, new BinaryBody(bytes.clone(), contentType), false, null);
    }

    Map<String, Object> patch(String path, Map<String, Object> body) {
        return performRequest("PATCH", path, null, body, false, null);
    }

    Map<String, Object> delete(String path) {
        return performRequest("DELETE", path, null, null, false, null);
    }

    Map<String, Object> delete(String path, Map<String, Object> query) {
        return performRequest("DELETE", path, query, null, false, null);
    }

    /**
     * Retry only what is safe to retry. 429 and 5xx are transient by definition; a 4xx
     * is the API saying the request itself is wrong, and retrying it only burns
     * rate-limit budget and delays the error the caller needs to see.
     */
    private static boolean isRetryableStatus(int status) {
        return status == 429 || status == 408 || (status >= 500 && status < 600);
    }

    private static double backoffSeconds(int attempt, Double retryAfter) {
        // The server's instruction wins — it knows when the window rolls over.
        if (retryAfter != null) {
            return retryAfter;
        }
        // Otherwise exponential with full jitter, so a fleet of workers limited at the
        // same moment does not retry in lockstep and re-limit itself.
        double ceiling = Math.min(8.0, 0.25 * Math.pow(2, attempt));
        return ThreadLocalRandom.current().nextDouble() * ceiling;
    }

    private static void sleep(double seconds) {
        try {
            Thread.sleep(Math.round(seconds * 1000));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SeatLayerConnectionException("Interrupted while backing off before a retry.", interrupted);
        }
    }

    private static double parseRetryAfter(Map<String, String> headers, Map<String, Object> body) {
        String header = headers.get("retry-after");
        if (header != null) {
            try {
                double seconds = Double.parseDouble(header.trim());
                if (seconds >= 0) {
                    return seconds;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the JSON field.
            }
        }
        // Fall back for routes that predate the headers.
        if (body.get("retryAfterSeconds") instanceof Number number) {
            return number.doubleValue();
        }
        return 1.0;
    }

    /** The default transport, over the JDK's own HTTP client. */
    private static final class JdkTransport implements Transport {

        private final java.net.http.HttpClient client;
        private final Duration timeout;

        JdkTransport(Duration timeout) {
            this.timeout = timeout;
            this.client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
        }

        @Override
        public Response send(String method, String url, Map<String, String> headers, String body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
            headers.forEach(builder::header);
            HttpRequest.BodyPublisher publisher;
            if (body == null) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else if (headers.getOrDefault("Content-Type", "").equals("application/json")) {
                publisher = HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            } else {
                publisher = HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.ISO_8859_1));
            }
            builder.method(method, publisher);

            try {
                HttpResponse<String> response =
                        client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                Map<String, String> responseHeaders = new LinkedHashMap<>();
                response.headers().map().forEach((name, values) -> {
                    if (!values.isEmpty()) {
                        responseHeaders.put(name.toLowerCase(java.util.Locale.ROOT), values.get(0));
                    }
                });

                return new Response(response.statusCode(), response.body(), responseHeaders);
            } catch (IOException error) {
                throw new SeatLayerConnectionException(
                        "Request to " + method + " " + url + " failed: " + error.getMessage(), error);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SeatLayerConnectionException("Request interrupted: " + method + " " + url, interrupted);
            }
        }
    }

    /** Build a body map, dropping null values so optional arguments stay optional. */
    static Map<String, Object> body(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("body() takes alternating keys and values");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            Object value = keyValuePairs[i + 1];
            if (value != null) {
                result.put(String.valueOf(keyValuePairs[i]), value);
            }
        }
        return result;
    }

    static Optional<String> stringAt(Map<String, Object> map, String key) {
        return map.get(key) instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
    }
}
