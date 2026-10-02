package com.ecommerce.FerreViky.dto.gruposDeProductos;


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class GruposDeProductosDTO {
    public record GrupoPublicoResponse(
            @Schema(description = "ID del grupo", example = "1")
            Long id,

            @Schema(description = "Nombre del grupo", example = "Herramientas eléctricas")
            String nombre,

            @Schema(description = "IDs de los productos pertenecientes al grupo", example = "[1, 2, 3]")
            List<Long> productoIds
    ) {}

    public record GrupoAdminResponse(
            @Schema(description = "ID del grupo", example = "1")
            Long id,

            @Schema(description = "Nombre del grupo", example = "Herramientas eléctricas")
            String nombre,

            @Schema(description = "Prefijo usado para generar la clave de los productos del grupo", example = "HE")
            String prefijoClave,

            @Schema(description = "Palabras comunes asociadas al grupo para búsqueda o categorización", example = "taladro, sierra, lijadora")
            String palabrasComunes,

            @Schema(description = "IDs de los productos pertenecientes al grupo", example = "[1, 2, 3]")
            List<Long> productoIds
    ) {}
}
