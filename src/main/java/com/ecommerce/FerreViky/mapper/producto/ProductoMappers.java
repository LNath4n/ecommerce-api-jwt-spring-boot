package com.ecommerce.FerreViky.mapper.producto;

import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.models.GrupoDeProductos;
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

    /**
     * Crea una entidad nueva a partir de un request.
     *
     * @param dto   datos de entrada
     * @param grupo grupo ya resuelto por el service, o {@code null} si no tiene grupo
     * @return entidad lista para guardarse
     */
    public static Producto toEntity(ProductoRequest dto, GrupoDeProductos grupo) {
        return actualizarEntidad(new Producto(), dto, grupo);
    }

    /**
     * Copia los datos del request sobre una entidad existente.
     * No toca el {@code id}.
     *
     * @param producto entidad a modificar
     * @param dto      datos nuevos
     * @param grupo    grupo ya resuelto, o {@code null}
     * @return Producto nuevo (o actualizado)
     */
    public static Producto actualizarEntidad(Producto producto, ProductoRequest dto, GrupoDeProductos grupo) {
        producto.setCodigo(dto.codigo());
        producto.setClave(dto.clave());
        producto.setDescripcion(dto.descripcion());
        producto.setMargenMercado(dto.margenMercado());
        producto.setCaja(dto.caja());
        producto.setMaster(dto.master());
        producto.setUnidad(dto.unidad());
        producto.setEan(dto.ean());
        producto.setPrecioMayoreoIva(dto.precioMayoreoIva());
        producto.setPrecioDistribuidorIva(dto.precioDistribuidorIva());
        producto.setPrecioPublicoIva(dto.precioPublicoIva());
        producto.setMarca(dto.marca());
        producto.setStock(dto.stock());
        producto.setGrupoDeProductos(grupo);
        return producto;
    }
}