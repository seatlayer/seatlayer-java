package io.seatlayer;

import java.time.Duration;
import java.util.Map;

/**
 * The SeatLayer client.
 *
 * <p>Secret-key only. This class must never run anywhere a ticket buyer can reach it —
 * browser surfaces get short-lived scoped tokens minted via {@link #sessions()}.
 *
 * <pre>{@code
 * SeatLayer seatlayer = new SeatLayer(System.getenv("SEATLAYER_SECRET_KEY"));
 * Map<String, Object> held = seatlayer.inventory().holdBestAvailable("summer-gala", 4);
 * }</pre>
 */
public final class SeatLayer {

    private final SeatLayerHttpClient http;
    private final Charts charts;
    private final Events events;
    private final Inventory inventory;
    private final Sessions sessions;
    private final Webhooks webhooks;
    private final Workspaces workspaces;

    public SeatLayer(String secretKey) {
        this(builder().secretKey(secretKey));
    }

    private SeatLayer(Builder builder) {
        this.http = new SeatLayerHttpClient(
                builder.secretKey, builder.baseUrl, builder.maxRetries, builder.timeout, builder.transport);
        this.charts = new Charts(http);
        this.events = new Events(http);
        this.inventory = new Inventory(http);
        this.sessions = new Sessions(http);
        this.webhooks = new Webhooks(http);
        this.workspaces = new Workspaces(http);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Charts charts() {
        return charts;
    }

    public Events events() {
        return events;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Sessions sessions() {
        return sessions;
    }

    public Webhooks webhooks() {
        return webhooks;
    }

    public Workspaces workspaces() {
        return workspaces;
    }

    /** {@code "live"} or {@code "test"}, derived from the key prefix. */
    public String mode() {
        return http.mode();
    }

    /** Dependency-aware readiness probe. */
    public Map<String, Object> ready() {
        return http.get("/health/ready");
    }

    /**
     * Escape hatch for surface this SDK does not wrap yet. Carries the same auth,
     * retries, idempotency and error mapping.
     */
    public Map<String, Object> request(
            String method, String path, Map<String, Object> query, Map<String, Object> body) {
        return http.request(method, path, query, body, null);
    }

    /** Builder for base URL, retries, timeout, and a custom transport. */
    public static final class Builder {
        private String secretKey;
        private String baseUrl = SeatLayerHttpClient.DEFAULT_BASE_URL;
        private int maxRetries = SeatLayerHttpClient.DEFAULT_MAX_RETRIES;
        private Duration timeout = SeatLayerHttpClient.DEFAULT_TIMEOUT;
        private SeatLayerHttpClient.Transport transport;

        public Builder secretKey(String value) {
            this.secretKey = value;
            return this;
        }

        public Builder baseUrl(String value) {
            this.baseUrl = value;
            return this;
        }

        /** Total attempts, not extra attempts. Default 3. */
        public Builder maxRetries(int value) {
            this.maxRetries = value;
            return this;
        }

        /** Per-attempt timeout. Default 30 seconds. */
        public Builder timeout(Duration value) {
            this.timeout = value;
            return this;
        }

        Builder transport(SeatLayerHttpClient.Transport value) {
            this.transport = value;
            return this;
        }

        public SeatLayer build() {
            return new SeatLayer(this);
        }
    }
}
