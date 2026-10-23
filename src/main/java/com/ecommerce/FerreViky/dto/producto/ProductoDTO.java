package com.ecommerce.FerreViky.dto.producto;

import com.ecommerce.FerreViky.models.Producto.TipoProducto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductoDTO {

        @Schema(description = "Respuesta al cliente al preguntar por un producto")
        public record ProductoPublicoResponse(
                @Schema(example = "2") Long id,
                @Schema(example = "steam-gift-card-200-mxn") String slug,
                @Schema(example = "STEAM-MX-200") String sku,
                @Schema(example = "Steam Gift Card $200 MXN") String nombre,
                String descripcion,
                @Schema(example = "Steam") String plataforma,
                TipoProducto tipo,
                @Schema(example = "MX") String region,
                @Schema(example = "200.00") BigDecimal valorNominal,
                @Schema(example = "MXN") String moneda,
                @Schema(example = "189.00") BigDecimal precioVenta,
                String instruccionesCanje,
                String imagenUrl,
                @Schema(description = "Códigos disponibles para la venta", example = "5") Integer stock
        ) {}

        @Schema(description = "Respuesta al administrador con todos los detalles del producto")
        public record ProductoAdminResponse(
                Long id,
                String sku,
                String slug,
                String nombre,
                String descripcion,
                String plataforma,
                TipoProducto tipo,
                String region,
                BigDecimal valorNominal,
                String moneda,
                BigDecimal precioVenta,
                BigDecimal precioCosto,
                String instruccionesCanje,
                Boolean activo,
                Long categoriaId,
                String imagenUrl,
                Integer stock,
                LocalDateTime fechaCreacion
        ) {}

        @Schema(description = "Datos de entrada para crear o actualizar un producto (administrador)")
        public record ProductoRequest(
                @NotBlank(message = "El SKU es obligatorio")
                @Schema(example = "STEAM-MX-200") String sku,

                @NotBlank(message = "El nombre es obligatorio")
                @Schema(example = "Steam Gift Card $200 MXN") String nombre,

                String descripcion,
                String plataforma,

                @NotNull(message = "El tipo es obligatorio")
                TipoProducto tipo,

                String region,
                BigDecimal valorNominal,
                String moneda,

                @NotNull(message = "El precio de venta es obligatorio")
                @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
                BigDecimal precioVenta,

                @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
                BigDecimal precioCosto,

                String instruccionesCanje,
                Boolean activo,

                @Schema(description = "ID de la categoría (opcional)", example = "3")
                Long categoriaId
        ) {}

        @Schema(description = "Categoría en formato resumido")
        public record CategoriaOpcionResponse(Long id, String nombre) {}
}