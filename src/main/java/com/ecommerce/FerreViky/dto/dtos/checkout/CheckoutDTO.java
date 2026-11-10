package com.ecommerce.FerreViky.dto.dtos.checkout;

import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import jakarta.validation.constraints.NotBlank;

public class CheckoutDTO {

    public record CheckoutResponse(String orderId) {}
    public record PaypalOrderRequest(@NotBlank String orderId) {}
    public record ConfirmResponse(Long orderId, OrderStatus status) {}
}
