package com.ecommerce.FerreViky.mapper.producto;

import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.models.Producto;

/**
 * Mappers para convertir entidades {@link Producto} a sus distintos DTOs de respuesta.
 */
public class ProductoMappers {

    /**
     * Convierte un producto a su vista pública, omitiendo campos sensibles como
     * precios de mayoreo/distribuidor, clave interna y márgenes.
     *
     * @param producto entidad a convertir
     * @return DTO con los campos visibles para el cliente final
     */
    public static ProductoPublicoResponse toPublicoResponse(Producto producto) {
        return new ProductoPublicoResponse(
                producto.getId(),
                producto.getCodigo(),
                producto.getDescripcion(),
                producto.getUnidad(),
                producto.getMarca(),
                producto.getPrecioPublicoIva(),
                producto.getStock()
        );
    }

    /**
     * Convierte un producto a su vista administrativa con todos sus campos.
     * <p>
     * Si el producto no pertenece a ningún grupo, se envía {@code null} como {@code grupoId}
     * en lugar de propagar un NPE.
     *
     * @param producto entidad a convertir
     * @return DTO completo incluyendo precios internos, márgenes y grupo asociado
     */
    public static ProductoAdminResponse toAdminResponse(Producto producto) {
        return new ProductoAdminResponse(
                producto.getId(),
                producto.getCodigo(),
                producto.getClave(),
                producto.getDescripcion(),
                producto.getMargenMercado(),
                producto.getCaja(),
                producto.getMaster(),
                producto.getUnidad(),
                producto.getEan(),
                producto.getPrecioMayoreoIva(),
                producto.getPrecioDistribuidorIva(),
                producto.getPrecioPublicoIva(),
                producto.getMarca(),
                producto.getGrupoDeProductos() != null ? producto.getGrupoDeProductos().getId() : null,
                producto.getStock()
        );
    }
}