package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.service.PaypalWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoint called by PayPal, not by Angular. It has no JWT: authenticity is proven
 * by the PayPal signature. It must be {@code permitAll} in the security config.
 */
@RestController
@RequestMapping("/webhooks/paypal")
@RequiredArgsConstructor
@Tag(name = "PayPal Webhooks", description = "Events sent by PayPal (not for the client app)")
public class PaypalWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaypalWebhookController.class);

    private final PaypalWebhookService webhookService;

    @Operation(
            summary = "Receive a PayPal webhook event",
            description = "Verifies the PayPal signature and processes the event. Any non-2xx response makes PayPal retry."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event received (processed or intentionally ignored)"),
            @ApiResponse(responseCode = "401", description = "Invalid PayPal signature"),
            @ApiResponse(responseCode = "500", description = "Temporary failure; PayPal will retry")
    })
    @PostMapping
    public ResponseEntity<Void> receive(
            // String on purpose: PayPal signs the exact raw body, it must not be parsed first
            @RequestBody String rawBody,
            @RequestHeader("PAYPAL-AUTH-ALGO") String authAlgo,
            @RequestHeader("PAYPAL-CERT-URL") String certUrl,
            @RequestHeader("PAYPAL-TRANSMISSION-ID") String transmissionId,
            @RequestHeader("PAYPAL-TRANSMISSION-SIG") String transmissionSig,
            @RequestHeader("PAYPAL-TRANSMISSION-TIME") String transmissionTime) {

        try {
            if (!webhookService.isValid(authAlgo, certUrl, transmissionId,
                    transmissionSig, transmissionTime, rawBody)) {
                log.warn("PayPal webhook rejected: invalid signature (transmission {})", transmissionId);
                return ResponseEntity.status(401).build();
            }
            webhookService.handle(rawBody);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("PayPal webhook {} failed, PayPal will retry", transmissionId, e);
            return ResponseEntity.status(500).build();
        }
    }
}