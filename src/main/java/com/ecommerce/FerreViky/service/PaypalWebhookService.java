package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.OrderNotPayableException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.PaymentNotCompletedException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.ReservationExpiredException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles PayPal webhooks, the safety net for buyers who approve the payment and then close
 * the tab before Angular calls {@code /checkout/confirm}.
 *
 * <p>Rules:
 * <ul>
 *   <li>The signature is always verified with PayPal before anything else.</li>
 *   <li>The payload is never trusted for money or status: the handlers only take the PayPal
 *       order id from it, and {@link CheckoutService} re-checks the real state with PayPal.</li>
 *   <li>Every handler is idempotent (PayPal retries and sends duplicates).</li>
 *   <li>An exception means "PayPal, send it again"; a normal return means "received".</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PaypalWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaypalWebhookService.class);

    private final PaypalService paypalService;
    private final CheckoutService checkoutService;
    private final JsonMapper jsonMapper;

    /**
     * @return {@code true} if the signature is valid
     * @throws RuntimeException if PayPal cannot be reached to verify
     */
    public boolean isValid(String authAlgo, String certUrl, String transmissionId,
                           String transmissionSig, String transmissionTime, String rawBody) {
        return paypalService.verifyWebhookSignature(
                authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime, rawBody);
    }

    /**
     * Processes an already verified event.
     *
     * @throws RuntimeException when PayPal should retry (order not saved yet, capture result
     *                          unknown, database error, ...)
     */
    public void handle(String rawBody) {
        JsonNode event = jsonMapper.readTree(rawBody);
        String eventId = event.path("id").asText();
        String type = event.path("event_type").asText();
        JsonNode resource = event.path("resource");
        log.info("PayPal webhook {} received ({})", eventId, type);

        switch (type) {
            // The buyer approved but may never have come back: capture it here
            case "CHECKOUT.ORDER.APPROVED" -> onApproved(resource.path("id").asText(null), eventId);

            // Capture finished or failed: resolve from the real PayPal state
            case "PAYMENT.CAPTURE.COMPLETED",
                 "PAYMENT.CAPTURE.DENIED",
                 "PAYMENT.CAPTURE.DECLINED" ->
                    onCapture(resource.at("/supplementary_data/related_ids/order_id").asText(null), eventId);

            // Money went back (our own refund, PayPal dashboard or a dispute): no automatic action
            case "PAYMENT.CAPTURE.REFUNDED",
                 "PAYMENT.CAPTURE.REVERSED" ->
                    log.warn("Webhook {} ({}): capture {} was refunded/reversed in PayPal, review it manually",
                            eventId, type, resource.path("id").asText());

            default -> log.debug("Webhook {} ignored, unhandled type {}", eventId, type);
        }
    }

    private void onApproved(String paypalOrderId, String eventId) {
        if (paypalOrderId == null) {
            log.warn("Webhook {}: approved event without order id, ignored", eventId);
            return;
        }
        try {
            checkoutService.confirmFromWebhook(paypalOrderId);
        } catch (OrderNotPayableException | ReservationExpiredException | PaymentNotCompletedException e) {
            // Business outcomes: retrying would give the same answer, so PayPal is told "received"
            log.info("Webhook {}: order {} not confirmed ({})", eventId, paypalOrderId, e.getMessage());
        } catch (HttpClientErrorException e) {
            // PayPal definitively rejected the capture, nothing was charged
            log.warn("Webhook {}: PayPal rejected capture of {} ({})", eventId, paypalOrderId, e.getStatusCode());
        }
        // Anything else (OrderNotFound, PaymentProcessing, DB) propagates so PayPal retries
    }

    private void onCapture(String paypalOrderId, String eventId) {
        if (paypalOrderId == null) {
            log.warn("Webhook {}: capture event without order id, ignored", eventId);
            return;
        }
        checkoutService.reconcileFromWebhook(paypalOrderId);
    }
}