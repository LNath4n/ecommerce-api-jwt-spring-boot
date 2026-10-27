package com.ecommerce.FerreViky.dto.dtos.producto;

import com.ecommerce.FerreViky.dto.validation.sinHtml.SinHtml;
import com.ecommerce.FerreViky.models.Producto.TipoProducto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductoDTO {

        @Schema(description = "Product details returned to customers")
        public record ProductoPublicoResponse(
                @Schema(description = "Product ID", example = "2")
                Long id,

                @Schema(description = "URL-friendly product identifier", example = "steam-gift-card-200-mxn")
                String slug,

                @Schema(description = "Stock keeping unit", example = "STEAM-MX-200")
                String sku,

                @Schema(description = "Product name", example = "Steam Gift Card $200 MXN")
                String nombre,

                @Schema(description = "Product description", example = "Digital gift card for the Steam store")
                String descripcion,

                @Schema(description = "Platform the product belongs to", example = "Steam")
                String plataforma,

                @Schema(description = "Product type")
                TipoProducto tipo,

                @Schema(description = "Region where the product can be redeemed", example = "MX")
                String region,

                @Schema(description = "Face value of the product", example = "200.00")
                BigDecimal valorNominal,

                @Schema(description = "ISO 4217 currency code", example = "MXN")
                String moneda,

                @Schema(description = "Sale price", example = "189.00")
                BigDecimal precioVenta,

                @Schema(description = "Instructions to redeem the code", example = "Go to Steam > Redeem a Steam Gift Card and enter the code")
                String instruccionesCanje,

                @Schema(description = "Product image URL", example = "https://cdn.example.com/products/steam-200.png")
                String imagenUrl,

                @Schema(description = "Codes available for sale", example = "5")
                Integer stock
        ) {}

        @Schema(description = "Full product details returned to administrators")
        public record ProductoAdminResponse(
                @Schema(description = "Product ID", example = "2")
                Long id,

                @Schema(description = "Stock keeping unit", example = "STEAM-MX-200")
                String sku,

                @Schema(description = "URL-friendly product identifier", example = "steam-gift-card-200-mxn")
                String slug,

                @Schema(description = "Product name", example = "Steam Gift Card $200 MXN")
                String nombre,

                @Schema(description = "Product description", example = "Digital gift card for the Steam store")
                String descripcion,

                @Schema(description = "Platform the product belongs to", example = "Steam")
                String plataforma,

                @Schema(description = "Product type")
                TipoProducto tipo,

                @Schema(description = "Region where the product can be redeemed", example = "MX")
                String region,

                @Schema(description = "Face value of the product", example = "200.00")
                BigDecimal valorNominal,

                @Schema(description = "ISO 4217 currency code", example = "MXN")
                String moneda,

                @Schema(description = "Sale price", example = "189.00")
                BigDecimal precioVenta,

                @Schema(description = "Cost price", example = "175.00")
                BigDecimal precioCosto,

                @Schema(description = "Instructions to redeem the code", example = "Go to Steam > Redeem a Steam Gift Card and enter the code")
                String instruccionesCanje,

                @Schema(description = "Whether the product is active", example = "true")
                Boolean activo,

                @Schema(description = "Category ID", example = "3")
                Long categoriaId,

                @Schema(description = "Product image URL", example = "https://cdn.example.com/products/steam-200.png")
                String imagenUrl,

                @Schema(description = "Codes available for sale", example = "5")
                Integer stock,

                @Schema(description = "Creation date and time", example = "2026-10-26T23:15:00")
                LocalDateTime fechaCreacion
        ) {}

        @Schema(description = "Input data to create or update a product (administrator)")
        public record ProductoRequest(
                @Schema(description = "Stock keeping unit", example = "STEAM-MX-200")
                @SinHtml
                @NotBlank(message = "SKU is required")
                @Size(max = 64, message = "SKU cannot exceed 64 characters")
                String sku,

                @Schema(description = "Product name", example = "Steam Gift Card $200 MXN")
                @SinHtml
                @NotBlank(message = "Name is required")
                @Size(max = 150, message = "Name cannot exceed 150 characters")
                @Pattern(
                        regexp = ".*[\\p{L}\\p{N}].*",
                        message = "Name must contain at least one letter or number"
                )
                String nombre,

                @Schema(description = "Product description (optional)", example = "Digital gift card for the Steam store")
                @SinHtml
                @Size(max = 2000, message = "Description cannot exceed 2000 characters")
                String descripcion,

                @Schema(description = "Platform the product belongs to (optional)", example = "Steam")
                @SinHtml
                @Size(max = 50, message = "Platform cannot exceed 50 characters")
                String plataforma,

                @Schema(description = "Product type")
                @NotNull(message = "Type is required")
                TipoProducto tipo,

                @Schema(description = "Region where the product can be redeemed (optional)", example = "MX")
                @SinHtml
                @Size(max = 10, message = "Region cannot exceed 10 characters")
                String region,

                @Schema(description = "Face value of the product (optional)", example = "200.00")
                @DecimalMin(value = "0.00", message = "Face value cannot be negative")
                @Digits(integer = 8, fraction = 2, message = "Face value format is invalid")
                BigDecimal valorNominal,

                @Schema(description = "ISO 4217 currency code (optional)", example = "MXN")
                @Pattern(regexp = "^[A-Z]{3}$", message = "Currency is invalid (e.g. MXN)")
                String moneda,

                @Schema(description = "Sale price", example = "189.00")
                @NotNull(message = "Sale price is required")
                @DecimalMin(value = "0.00", message = "Sale price cannot be negative")
                @Digits(integer = 8, fraction = 2, message = "Sale price format is invalid")
                BigDecimal precioVenta,

                @Schema(description = "Cost price (optional)", example = "175.00")
                @DecimalMin(value = "0.00", message = "Cost price cannot be negative")
                @Digits(integer = 8, fraction = 2, message = "Cost price format is invalid")
                BigDecimal precioCosto,

                @Schema(description = "Instructions to redeem the code (optional)", example = "Go to Steam > Redeem a Steam Gift Card and enter the code")
                @SinHtml
                @Size(max = 2000, message = "Redemption instructions cannot exceed 2000 characters")
                String instruccionesCanje,

                @Schema(description = "Whether the product is active (optional)", example = "true")
                Boolean activo,

                @Schema(description = "Category ID (optional)", example = "3")
                Long categoriaId
        ) {}

        @Schema(description = "Category in summary format")
        public record CategoriaOpcionResponse(
                @Schema(description = "Category ID", example = "3")
                Long id,

                @Schema(description = "Category name", example = "Gift Cards")
                String nombre
        ) {}
}