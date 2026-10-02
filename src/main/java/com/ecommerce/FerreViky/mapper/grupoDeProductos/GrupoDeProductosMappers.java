package com.ecommerce.FerreViky.mapper.grupoDeProductos;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoPublicoResponse;
import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoAdminResponse;
import com.ecommerce.FerreViky.models.GrupoDeProductos;
import com.ecommerce.FerreViky.models.Producto;

import java.util.Collections;
import java.util.List;

/**
 * Mappers para convertir entidades {@link GrupoDeProductos} a sus distintos DTOs de respuesta.
 * <p>
 * Si el grupo no tiene estilos asignados, se retorna una lista vacía en lugar de {@code null}.
 */
public class GrupoDeProductosMappers {

    /**
     * Convierte un grupo a su vista pública, exponiendo únicamente los IDs de sus estilos.
     *
     * @param grupo entidad a convertir; sus estilos pueden ser {@code null}
     * @return DTO con id, nombre y lista de IDs de productos asociados
     */
    public static GrupoPublicoResponse toPublicoResponse(GrupoDeProductos grupo) {
        List<Long> ids = grupo.getEstilos() != null
                ? grupo.getEstilos().stream().map(Producto::getId).toList()
                : Collections.emptyList();

        return new GrupoPublicoResponse(
                grupo.getId(),
                grupo.getNombre(),
                ids
        );
    }

    /**
     * Convierte un grupo a su vista administrativa, incluyendo campos internos como
     * {@code prefijoClave} y {@code palabrasComunes}.
     *
     * @param grupo entidad a convertir; sus estilos pueden ser {@code null}
     * @return DTO con todos los campos del grupo, incluyendo los de uso interno
     */
    public static GrupoAdminResponse toAdminResponse(GrupoDeProductos grupo) {
        List<Long> ids = grupo.getEstilos() != null
                ? grupo.getEstilos().stream().map(Producto::getId).toList()
                : Collections.emptyList();

        return new GrupoAdminResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getPrefijoClave(),
                grupo.getPalabrasComunes(),
                ids
        );
    }
}