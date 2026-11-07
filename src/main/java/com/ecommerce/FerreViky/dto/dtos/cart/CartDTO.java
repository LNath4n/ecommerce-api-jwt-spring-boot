package com.ecommerce.FerreViky.dto.dtos.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CartDTO {
    @Schema(description = "Request to add a product to the cart")
    public record AddCartItemRequest(
            @Schema(description = "Product ID", example = "2")
            @NotNull(message = "Product ID is required")
            Long productId,

            @Schema(description = "Number of codes requested", example = "1")
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 100, message = "Maximum quantity per request is 100")
            Integer quantity
    ) {}

    @Schema(description = "Request to update the quantity of a product in the cart")
    public record UpdateCartItemQuantityRequest(
            @Schema(description = "Product ID", example = "2")
            @NotNull(message = "Product ID is required")
            Long productId,

            @Schema(description = "New quantity (0 removes the product from the cart)", example = "3")
            @NotNull(message = "Quantity is required")
            @Min(value = 0, message = "Quantity cannot be negative")
            @Max(100)
            Integer quantity
    ) {}

    @Schema(description = "Product entry inside the cart")
    public record CartItemResponse(
            @Schema(description = "Product ID", example = "2")
            Long productId,

            @Schema(description = "URL-friendly product identifier", example = "steam-gift-card-200-mxn")
            String slug,

            @Schema(description = "Product name", example = "Steam Gift Card $200 MXN")
            String name,

            @Schema(description = "Stock keeping unit", example = "STEAM-MX-200")
            String sku,

            @Schema(description = "Platform the product belongs to", example = "Steam")
            String platform,

            @Schema(description = "Quantity in the cart", example = "1")
            Integer quantity,

            @Schema(description = "Sale price", example = "189.00")
            BigDecimal salePrice,

            @Schema(description = "Product image URL", example = "https://cdn.example.com/products/steam-200.png")
            String imageUrl
    ) {}

    @Schema(description = "Cart contents and totals")
    public record CartResponse(
            @Schema(description = "Products in the cart")
            List<CartItemResponse> items,

            @Schema(description = "Cart creation date and time", example = "2026-10-26T23:18:00")
            LocalDateTime createdAt,

            @Schema(description = "Cart subtotal", example = "189.00")
            BigDecimal subtotal
    ) {}
}