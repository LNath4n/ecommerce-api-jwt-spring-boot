package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.producto.ProductoDTO.CategoriaOpcionResponse;
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
@Tag(name = "Productos", description = "Consulta del catálogo de códigos virtuales")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar productos", description = "Retorna los productos activos del catálogo paginados, con su stock de códigos disponibles")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public Page<ProductoPublicoResponse> obtenerTodos(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productoService.obtenerTodos(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por ID", description = "Retorna un producto activo según su ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<ProductoPublicoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerProductoPorId(id));
    }

    @GetMapping("/plataformas")
    @Operation(summary = "Listar plataformas", description = "Retorna las plataformas distintas del catálogo (Steam, Netflix, etc.), ordenadas alfabéticamente")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public List<String> obtenerPlataformas() {
        return productoService.obtenerPlataformas();
    }

    @GetMapping("/categorias")
    @Operation(summary = "Listar categorías", description = "Retorna todas las categorías sin paginar, solo con id y nombre")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")})
    public List<CategoriaOpcionResponse> obtenerListaDeCategorias() {
        return productoService.obtenerListaDeCategorias();
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductoPublicoResponse> obtenerPorSlug(@PathVariable String slug) {
        return ResponseEntity.ok(productoService.obtenerProductoPorSlug(slug));
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar productos", description = "Busca por texto libre en nombre, SKU, plataforma y descripción. Plataforma y categoría son filtros opcionales")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Resultados obtenidos exitosamente")})
    public Page<ProductoPublicoResponse> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String plataforma,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productoService.buscar(q, plataforma, categoriaId, PageRequest.of(page, size));
    }
}