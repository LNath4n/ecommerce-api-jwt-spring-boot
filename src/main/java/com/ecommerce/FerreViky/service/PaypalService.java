package com.ecommerce.FerreViky.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PaypalService.class);

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
        if (Instant.now().isBefore(tokenExpiry)) {
            log.trace("Using cached PayPal access token");
            return accessToken;
        }

        log.info("Requesting a new PayPal access token");
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
        log.info("New PayPal access token obtained, cached until {}", tokenExpiry);
        return accessToken;
    }

    public String createOrder(BigDecimal total, String currency, String description) {
        log.debug("Calling PayPal createOrder: total={} {} description={}", total, currency, description);
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

        String paypalOrderId = res.get("id").asText();
        log.info("PayPal order {} created (total={} {})", paypalOrderId, total, currency);
        return paypalOrderId;
    }

    public JsonNode captureOrder(String orderId, String requestId) {
        log.info("Calling PayPal capture for order {} (requestId {})", orderId, requestId);
        JsonNode res = rest.post()
                .uri("/v2/checkout/orders/{id}/capture", orderId)
                .headers(h -> {
                    h.setBearerAuth(getAccessToken());
                    h.set("PayPal-Request-Id", requestId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
        log.info("PayPal capture for order {} responded with status {}",
                orderId, res == null ? null : res.path("status").asText());
        return res;
    }

    public JsonNode getOrder(String orderId) {
        log.debug("Calling PayPal getOrder for order {}", orderId);
        JsonNode res = rest.get()
                .uri("/v2/checkout/orders/{id}", orderId)
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .retrieve()
                .body(JsonNode.class);
        log.debug("PayPal order {} has status {}", orderId, res == null ? null : res.path("status").asText());
        return res;
    }

    public String refundCapture(String captureId, String requestId) {
        log.info("Calling PayPal refund for capture {} (requestId {})", captureId, requestId);
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
        String status = res.get("status").asText();
        log.info("PayPal refund for capture {} responded with status {}", captureId, status);
        return status;   // COMPLETED, PENDING, FAILED...
    }
}