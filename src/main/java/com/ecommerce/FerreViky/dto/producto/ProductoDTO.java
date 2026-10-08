package com.ecommerce.FerreViky.dto.producto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class ProductoDTO {

    @Schema(description = "Respuesta al cliente al preguntar por un producto")
    public record ProductoPublicoResponse(
            @Schema(description = "ID generado para el producto",example = "2")
            Long id,
            @Schema(description = "ID que utiliza el catalogo de productos del cliente",example = "100101")
            String codigo,
            @Schema(description = "Nombre del Producto",example = "Estuche con 50 cuchillas SK4 de 18 mm para cutter, TRUPER")
            String descripcion,
            @Schema(description = "Unidad en la que viene el producto",example = "Set")
            String unidad,
            @Schema(description = "Marca del producto",example = "TRUPER")
            String marca,
            @Schema(description = "Precio que vera el cliente",example = "135.00")
            BigDecimal precioPublicoIva,
            @Schema(description = "Cantidad del stock disponible",example = "5")
            Integer stock
    ) {}

    @Schema(description = "Respuesta al administrador con todos los detalles del producto")
    public record ProductoAdminResponse(
            @Schema(description = "ID generado para el producto", example = "2")
            Long id,

            @Schema(description = "ID del catálogo del cliente", example = "100048")
            String codigo,

            @Schema(description = "SKU o clave corta del producto", example = "PET-15X")
            String clave,

            @Schema(description = "Nombre completo del producto", example = "Llave ajustable 15' cromada, TRUPER")
            String descripcion,

            @Schema(description = "Segmento de precio (ej: MM00 = mayoreo)", example = "MM00")
            String margenMercado,

            @Schema(description = "Cantidad mínima por caja", example = "2")
            String caja,

            @Schema(description = "Cantidad por master/pallet", example = "12")
            String master,

            @Schema(description = "Unidad de venta del producto", example = "Pieza")
            String unidad,

            @Schema(description = "Código de barras EAN-13", example = "7506240674542")
            String ean,

            @Schema(description = "Precio para mayoristas con IVA", example = "485.00")
            BigDecimal precioMayoreoIva,

            @Schema(description = "Precio para distribuidores con IVA", example = "375.00")
            BigDecimal precioDistribuidorIva,

            @Schema(description = "Precio al público con IVA", example = "585.00")
            BigDecimal precioPublicoIva,

            @Schema(description = "Marca del producto", example = "Truper")
            String marca,

            @Schema(description = "ID del grupo de productos al que pertenece", example = "3")
            Long grupoDeProductosId,

            @Schema(description = "Stock disponible", example = "50")
            Integer stock
    ) {}

    @Schema(description = "Datos de entrada para crear o actualizar un producto (administrador)")
    public record ProductoRequest(
            @Schema(description = "ID del catálogo del cliente", example = "100048")
            String codigo,

            @NotBlank(message = "La clave es obligatoria")
            @Schema(description = "SKU o clave corta del producto (única)", example = "PET-15X")
            String clave,

            @Schema(description = "Nombre completo del producto", example = "Llave ajustable 15' cromada, TRUPER")
            String descripcion,

            @Schema(description = "Segmento de precio (ej: MM00 = mayoreo)", example = "MM00")
            String margenMercado,

            @Schema(description = "Cantidad mínima por caja", example = "2")
            String caja,

            @Schema(description = "Cantidad por master/pallet", example = "12")
            String master,

            @Schema(description = "Unidad de venta del producto", example = "Pieza")
            String unidad,

            @Schema(description = "Código de barras EAN-13", example = "7506240674542")
            String ean,

            @Schema(description = "Precio para mayoristas con IVA", example = "485.00")
            BigDecimal precioMayoreoIva,

            @Schema(description = "Precio para distribuidores con IVA", example = "375.00")
            BigDecimal precioDistribuidorIva,

            @Schema(description = "Precio al público con IVA", example = "585.00")
            BigDecimal precioPublicoIva,

            @Schema(description = "Marca del producto", example = "Truper")
            String marca,

            @Schema(description = "ID del grupo de productos (opcional, puede ser null)", example = "3")
            Long grupoDeProductosId,

            @PositiveOrZero(message = "El stock debe ser mayor o igual a 0")
            @Schema(description = "Stock disponible", example = "50")
            Integer stock
    ) {}
}