package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoOpcionResponse;
import com.ecommerce.FerreViky.dto.gruposDeProductos.GruposDeProductosDTO.GrupoPublicoResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@CrossOrigin("*")
@RequestMapping("/productos")
@Tag(name = "Productos", description = "Consulta, creación y eliminación de productos del catálogo")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar todos los productos", description = "Retorna todos los productos del catálogo paginados")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public Page<ProductoPublicoResponse> obtenerTodos(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productoService.obtenerTodos(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por ID", description = "Retorna un producto específico según su ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Producto encontrado"), @ApiResponse(responseCode = "404", description = "Producto no encontrado")})
    public ResponseEntity<ProductoPublicoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerProductoPorId(id));
    }

    @GetMapping("/grupos")
    @Operation(summary = "Listar grupos de productos", description = "Retorna todos los grupos de productos paginados")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public Page<GrupoPublicoResponse> obtenerTodosLosGrupos(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productoService.obtenerTodosLosGrupos(pageable);
    }

    @GetMapping("/grupos/{id}")
    @Operation(summary = "Obtener grupo por ID", description = "Retorna un grupo de productos y sus productos asociados")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Grupo encontrado"), @ApiResponse(responseCode = "404", description = "Grupo no encontrado")})
    public ResponseEntity<GrupoPublicoResponse> obtenerGrupoPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerGrupoPorId(id));
    }

    @GetMapping("/marcas")
    @Operation(summary = "Listar marcas", description = "Retorna la lista de marcas distintas del catálogo, ordenadas alfabéticamente")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public List<String> obtenerMarcas() {
        return productoService.obtenerMarcas();
    }

    @GetMapping("/grupos/lista")
    @Operation(summary = "Listar grupos (resumen)", description = "Retorna todos los grupos de productos sin paginar, solo con id y nombre")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public List<GrupoOpcionResponse> obtenerListaDeGrupos() {
        return productoService.obtenerListaDeGrupos();
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar productos", description = "Busca por texto libre en descripción, código, clave y marca. Marca y grupo son filtros opcionales")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultados obtenidos exitosamente")
    })
    public Page<ProductoPublicoResponse> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productoService.buscar(q, marca, grupoId, PageRequest.of(page, size));
    }

}