package com.ecommerce.FerreViky.dto.dtos.carrito;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CarritoDTO {
    @Schema(description = "Request to add a product to the cart")
    public record AgregarCarrito(
            @Schema(description = "Product ID", example = "2")
            @NotNull(message = "Product ID is required")
            Long idProducto,

            @Schema(description = "Number of codes requested", example = "1")
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 100, message = "Maximum quantity per request is 100")
            Integer cantidad
    ) {}

    @Schema(description = "Request to update the quantity of a product in the cart")
    public record ActualizarCantidad(
            @Schema(description = "Product ID", example = "2")
            @NotNull(message = "Product ID is required")
            Long idProducto,

            @Schema(description = "New quantity (0 removes the product from the cart)", example = "3")
            @Min(value = 0, message = "Quantity cannot be negative")
            int cantidad
    ) {}

    @Schema(description = "Product entry inside the cart")
    public record CarritoProductoDTO(
            @Schema(description = "Product ID", example = "2")
            Long productoId,

            @Schema(description = "URL-friendly product identifier", example = "steam-gift-card-200-mxn")
            String slug,

            @Schema(description = "Product name", example = "Steam Gift Card $200 MXN")
            String nombre,

            @Schema(description = "Stock keeping unit", example = "STEAM-MX-200")
            String sku,

            @Schema(description = "Platform the product belongs to", example = "Steam")
            String plataforma,

            @Schema(description = "Quantity in the cart", example = "1")
            Integer cantidad,

            @Schema(description = "Sale price", example = "189.00")
            BigDecimal precioVenta,

            @Schema(description = "Product image URL", example = "https://cdn.example.com/products/steam-200.png")
            String imagenUrl
    ) {}

    @Schema(description = "Cart contents and totals")
    public record CarritoResponseDTO(
            @Schema(description = "Products in the cart")
            List<CarritoProductoDTO> productos,

            @Schema(description = "Cart creation date and time", example = "2026-10-26T23:18:00")
            LocalDateTime fechaCreacion,

            @Schema(description = "Cart subtotal", example = "189.00")
            BigDecimal subtotal
    ) {}
}