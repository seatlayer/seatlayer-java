package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.List;
import java.util.Map;

/** Manage webhook subscriptions. To VERIFY a delivery, see {@link Webhook}. */
public final class Webhooks {

    private final SeatLayerHttpClient http;

    Webhooks(SeatLayerHttpClient http) {
        this.http = http;
    }

    public Map<String, Object> list() {
        return http.get("/v1/webhooks");
    }

    public Map<String, Object> create(String url, List<String> events) {
        return http.post("/v1/webhooks", body("url", url, "events", events));
    }

    public Map<String, Object> update(String webhookId, Map<String, Object> fields) {
        return http.patch("/v1/webhooks/" + encode(webhookId), fields);
    }

    public Map<String, Object> delete(String webhookId) {
        return http.delete("/v1/webhooks/" + encode(webhookId));
    }

    public Map<String, Object> listDeliveries(String webhookId) {
        return http.get("/v1/webhooks/" + encode(webhookId) + "/deliveries");
    }
}
