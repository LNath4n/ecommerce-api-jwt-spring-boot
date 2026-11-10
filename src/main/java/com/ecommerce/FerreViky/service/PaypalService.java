package com.ecommerce.FerreViky.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PaypalService {

    private final RestClient rest;
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public PaypalService(RestClient paypalRestClient,
                         @Value("${paypal.client-id}") String clientId,
                         @Value("${paypal.client-secret}") String clientSecret) {
        this.rest = paypalRestClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // Token OAuth2 con caché: solo pide uno nuevo cuando está por expirar
    private synchronized String getAccessToken() {
        if (Instant.now().isBefore(tokenExpiry)) return accessToken;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        JsonNode res = rest.post()
                .uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        accessToken = res.get("access_token").asText();
        tokenExpiry = Instant.now().plusSeconds(res.get("expires_in").asLong() - 60);
        return accessToken;
    }

    public String createOrder(BigDecimal total, String currency, String description) {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "description", description,
                        "amount", Map.of(
                                "currency_code", currency,
                                "value", total.setScale(2, RoundingMode.HALF_UP).toPlainString()))),
                "payment_source", Map.of(
                        "paypal", Map.of(
                                "experience_context", Map.of(
                                        "shipping_preference", "NO_SHIPPING",
                                        "landing_page", "LOGIN",
                                        "user_action", "PAY_NOW"))));

        JsonNode res = rest.post()
                .uri("/v2/checkout/orders")
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        return res.get("id").asText();
    }

    public JsonNode captureOrder(String orderId, String requestId) {
        return rest.post()
                .uri("/v2/checkout/orders/{id}/capture", orderId)
                .headers(h -> {
                    h.setBearerAuth(getAccessToken());
                    h.set("PayPal-Request-Id", requestId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getOrder(String orderId) {
        return rest.get()
                .uri("/v2/checkout/orders/{id}", orderId)
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .retrieve()
                .body(JsonNode.class);
    }

    public String refundCapture(String captureId, String requestId) {
        JsonNode res = rest.post()
                .uri("/v2/payments/captures/{id}/refund", captureId)
                .headers(h -> {
                    h.setBearerAuth(getAccessToken());
                    h.set("PayPal-Request-Id", requestId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
        return res.get("status").asText();   // COMPLETED, PENDING, FAILED...
    }
}