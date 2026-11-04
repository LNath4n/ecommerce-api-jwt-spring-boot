package com.ecommerce.FerreViky.mapper.producto;

import static com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoPublicoResponse;
import static com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoAdminResponse;
import static com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoRequest;

import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.models.Producto;

public class ProductoMappers {

    public static ProductoPublicoResponse toPublicoResponse(
            Producto p, String imagenUrl, int stock) {
        return new ProductoPublicoResponse(
                p.getId(),
                p.getSlug(),
                p.getSku(),
                p.getNombre(),
                p.getDescripcion(),
                p.getPlataforma(),
                p.getTipo(),
                p.getRegion(),
                p.getValorNominal(),
                p.getMoneda(),
                p.getPrecioVenta(),
                p.getInstruccionesCanje(),
                imagenUrl,
                stock,
                p.getVistas(),
                p.getPromedioCalificacion(),
                p.getTotalResenas()
        );
    }

    public static ProductoAdminResponse toAdminResponse(
            Producto p, String imagenUrl, int stock) {
        return new ProductoAdminResponse(
                p.getId(),
                p.getSku(),
                p.getSlug(),
                p.getNombre(),
                p.getDescripcion(),
                p.getPlataforma(),
                p.getTipo(),
                p.getRegion(),
                p.getValorNominal(),
                p.getMoneda(),
                p.getPrecioVenta(),
                p.getPrecioCosto(),
                p.getInstruccionesCanje(),
                p.getActivo(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                imagenUrl,
                stock,
                p.getFechaCreacion(),
                p.getVistas(),
                p.getPromedioCalificacion(),
                p.getTotalResenas()
        );
    }

    public static Producto toEntity(ProductoRequest dto, Categoria categoria) {
        return actualizarEntidad(new Producto(), dto, categoria);
    }

    public static Producto actualizarEntidad(
            Producto p, ProductoRequest dto, Categoria categoria) {
        p.setSku(dto.sku());
        p.setNombre(dto.nombre());
        p.setDescripcion(dto.descripcion());
        p.setPlataforma(dto.plataforma());
        p.setTipo(dto.tipo());
        p.setRegion(dto.region());
        p.setValorNominal(dto.valorNominal());
        p.setMoneda(dto.moneda());
        p.setPrecioVenta(dto.precioVenta());
        p.setPrecioCosto(dto.precioCosto());
        p.setInstruccionesCanje(dto.instruccionesCanje());

        if (dto.activo() != null) {
            p.setActivo(dto.activo());
        }

        p.setCategoria(categoria);
        return p;
    }
}