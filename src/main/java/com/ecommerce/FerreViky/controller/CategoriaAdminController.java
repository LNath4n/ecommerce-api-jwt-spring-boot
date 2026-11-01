package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.categoria.CategoriaDTO.CategoriaRequest;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.CategoriaOpcionResponse;
import com.ecommerce.FerreViky.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/admin/categorias")
@RestController
@AllArgsConstructor
public class CategoriaAdminController {

    private final CategoriaService categoriaService;

    @PostMapping
    @Operation(summary = "Crear categoría", description = "Crea una categoría nueva. El nombre debe ser único (sin distinguir mayúsculas)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de la categoría inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre")
    })
    public ResponseEntity<CategoriaOpcionResponse> crearCategoria(@Validated @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(categoriaService.crearCategoria(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar categoría", description = "Cambia el nombre de la categoría. El nombre debe seguir siendo único")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría actualizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de la categoría inválidos"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada"),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre")
    })
    public ResponseEntity<CategoriaOpcionResponse> editarCategoria(
            @Parameter(description = "ID de la categoría a editar", example = "1") @PathVariable Long id,
            @Validated @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(categoriaService.editarCategoria(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar categoría", description = "Elimina una categoría. Si tiene productos asociados no se puede borrar: reasígnalos o quítales la categoría primero")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría borrada con éxito"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada"),
            @ApiResponse(responseCode = "409", description = "La categoría tiene productos asociados")
    })
    public ResponseEntity<String> borrarCategoria(
            @Parameter(description = "ID de la categoría a borrar", example = "1") @PathVariable Long id) {
        categoriaService.borrarCategoria(id);
        return ResponseEntity.ok("Categoria borrada con exito");
    }
}