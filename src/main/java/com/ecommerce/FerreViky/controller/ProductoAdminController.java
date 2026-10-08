package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.producto.ProductoDTO;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/admin/productos")
@RestController
@AllArgsConstructor
public class ProductoAdminController {

    private final ProductoService productoService;

    @PostMapping()
    @Operation(summary = "Crear producto", description = "Crea un producto nuevo. Si se indica grupoDeProductosId, el grupo debe existir; si es null, el producto se crea sin grupo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos del producto inválidos"),
            @ApiResponse(responseCode = "404", description = "Grupo de productos no encontrado")
    })
    public ResponseEntity<ProductoPublicoResponse> crearProducto(@Validated @RequestBody ProductoRequest productoAdminRequest) {
        return ResponseEntity.ok(productoService.crearProducto(productoAdminRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar producto", description = "Elimina un producto según su ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto borrado con éxito"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<String> borrarUnProducto(
            @Parameter(description = "ID del producto a borrar", example = "1") @PathVariable Long id) {
        productoService.borrarProducto(id);
        return ResponseEntity.ok("Producto borrado con exito");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar producto", description = "Reemplaza todos los datos del producto con los del request (semántica PUT). Los campos null se guardan como null")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos del producto inválidos"),
            @ApiResponse(responseCode = "404", description = "Producto o grupo de productos no encontrado")
    })
    public ResponseEntity<ProductoPublicoResponse> editarProducto(
            @Validated @RequestBody ProductoRequest productoAdminRequest,
            @Parameter(description = "ID del producto a editar", example = "1") @PathVariable Long id) {

        return ResponseEntity.ok(productoService.editarProducto(productoAdminRequest, id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por ID", description = "Retorna la vista de administración de un producto específico según su ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<ProductoAdminResponse> obtenerProducto(
            @Parameter(description = "ID del producto a consultar", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerUnProducto(id));
    }
}
