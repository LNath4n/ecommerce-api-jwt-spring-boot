package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.checkout.CheckoutDTO.CheckoutResponse;
import com.ecommerce.FerreViky.dto.dtos.checkout.CheckoutDTO.ConfirmResponse;
import com.ecommerce.FerreViky.dto.dtos.checkout.CheckoutDTO.PaypalOrderRequest;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Orders;
import com.ecommerce.FerreViky.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Create and manage checkout payments")
@SecurityRequirement(name = "bearerAuth")
public class CheckoutController {

    private final CheckoutService checkoutService;

    @Operation(
            summary = "Create a checkout order",
            description = "Creates a pending order from the authenticated customer's cart, reserves its available codes, and creates a PayPal order. Returns the PayPal order ID for the client to start the approval flow."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Checkout and PayPal order created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CheckoutResponse.class),
                            examples = @ExampleObject(value = "{\"orderId\":\"5O190127TN364715T\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The cart is empty or requested stock is unavailable",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(name = "EmptyCart", value = "The cart is empty"),
                                    @ExampleObject(
                                            name = "InsufficientStock",
                                            value = "Cannot have 3 units of Steam Gift Card $200 MXN; only 2 are in stock"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT is missing or invalid",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer has no cart or a cart product is no longer available",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(value = "No cart found for customer 42"),
                                    @ExampleObject(value = "Product not found with ID: 2")
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "PayPal order creation failed; the pending order is cancelled and its code reservations are released",
                    content = @Content
            )
    })
    @PostMapping
    public CheckoutResponse checkout(@AuthenticationPrincipal Customer customer) {
        return new CheckoutResponse(checkoutService.checkout(customer));
    }

    @Operation(
            summary = "Confirm a PayPal payment",
            description = "Captures the authenticated customer's approved PayPal order and returns the resulting internal order ID and status. Repeating a completed confirmation is idempotent."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "The PayPal order ID returned by the checkout endpoint",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaypalOrderRequest.class),
                    examples = @ExampleObject(value = "{\"orderId\":\"5O190127TN364715T\"}")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment was processed; status is the resulting order status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfirmResponse.class),
                            examples = @ExampleObject(value = "{\"orderId\":42,\"status\":\"PAID\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The request body is invalid or orderId is blank",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"orderId\":\"must not be blank\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT is missing or invalid",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The PayPal order does not belong to the authenticated customer or was not found",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Order not found")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The order cannot be paid, its reservation expired, PayPal did not complete the payment, or another confirmation is still processing",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(value = "Order 42 cannot be paid because its status is CANCELLED"),
                                    @ExampleObject(value = "The reservation for order 42 has expired"),
                                    @ExampleObject(value = "The payment was not completed"),
                                    @ExampleObject(value = "Payment for order 42 is still being processed")
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "An unexpected error occurred while capturing or finalizing the payment; check the order status before retrying",
                    content = @Content
            )
    })
    @PostMapping("/confirm")
    public ConfirmResponse confirm(@Valid @RequestBody PaypalOrderRequest req,
                                   @AuthenticationPrincipal Customer customer) {
        Orders order = checkoutService.confirmPayment(req.orderId(), customer);
        return new ConfirmResponse(order.getId(), order.getStatus());
    }

    @Operation(
            summary = "Cancel a checkout order",
            description = "Releases the reserved codes for the authenticated customer's pending PayPal order. Orders that are no longer pending are left unchanged."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "The PayPal order ID to cancel",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PaypalOrderRequest.class),
                    examples = @ExampleObject(value = "{\"orderId\":\"5O190127TN364715T\"}")
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The cancellation request was processed; no response body"),
            @ApiResponse(
                    responseCode = "400",
                    description = "The request body is invalid or orderId is blank",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"orderId\":\"must not be blank\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT is missing or invalid",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The PayPal order does not belong to the authenticated customer or was not found",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Order not found")
                    )
            )
    })
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Valid @RequestBody PaypalOrderRequest req,
                       @AuthenticationPrincipal Customer customer) {
        checkoutService.cancelOrder(req.orderId(), customer);
    }
}