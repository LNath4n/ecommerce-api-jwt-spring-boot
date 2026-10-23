package com.ecommerce.FerreViky.dto.carrito;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CarritoDTO {

    public record AgregarCarrito(
            @Schema(description = "ID perteneciente al Producto", example = "2")
            @NotNull Long idProducto,

            @Schema(description = "Cantidad de códigos que se quieren", example = "1")
            @NotNull @Min(1) Integer cantidad
    ) {}

    public record ActualizarCantidad(
            @NotNull Long idProducto,
            @Min(0) int cantidad
    ) {}

    public record CarritoProductoDTO(
            Long productoId,
            String slug,
            String nombre,
            String sku,
            String plataforma,
            Integer cantidad,
            BigDecimal precioVenta,
            String imagenUrl
    ) {}

    public record CarritoResponseDTO(
            Long id,
            List<CarritoProductoDTO> productos,
            LocalDateTime fechaCreacion,
            BigDecimal subtotal
    ) {}
}