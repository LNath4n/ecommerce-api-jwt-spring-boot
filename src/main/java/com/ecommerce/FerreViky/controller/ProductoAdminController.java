package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RequestMapping("/admin/productos")
@RestController
@AllArgsConstructor
public class ProductoAdminController {

    private final ProductoService productoService;

    @PostMapping
    @Operation(summary = "Crear producto", description = "Crea un producto nuevo. Si se indica categoriaId, la categoría debe existir; si es null, el producto se crea sin categoría. El stock no se envía: se calcula con los códigos cargados. La imagen se sube aparte en POST /admin/productos/{id}/imagen")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos del producto inválidos"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada"),
            @ApiResponse(responseCode = "409", description = "Ya existe un producto con ese SKU")
    })
    public ResponseEntity<ProductoPublicoResponse> crearProducto(@Validated @RequestBody ProductoRequest productoAdminRequest) {
        return ResponseEntity.ok(productoService.crearProducto(productoAdminRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar producto", description = "Elimina un producto y su imagen del bucket. Si ya tiene códigos o pedidos asociados no se puede borrar: desactívalo con activo = false")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto borrado con éxito"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "409", description = "El producto tiene códigos o pedidos asociados")
    })
    public ResponseEntity<String> borrarUnProducto(
            @Parameter(description = "ID del producto a borrar", example = "1") @PathVariable Long id) {
        productoService.borrarProducto(id);
        return ResponseEntity.ok("Producto borrado con exito");
    }

    @PatchMapping("/{id}/desactivar")
    @Operation(summary = "Desactivar producto", description = "Desactiva un producto sin eliminarlo: deja de aparecer en el catálogo público pero conserva sus códigos, pedidos e imagen. Úsalo cuando el producto ya no se pueda borrar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto desactivado con éxito"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<String> desactivarUnProducto(
            @Parameter(description = "ID del producto a desactivar", example = "1") @PathVariable Long id) {
        productoService.desactivarProducto(id);
        return ResponseEntity.ok("Producto desactivado con exito");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar producto", description = "Reemplaza los datos del producto con los del request (semántica PUT). Los campos null se guardan como null, salvo 'activo', que conserva su valor si no se envía. No modifica la imagen")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos del producto inválidos"),
            @ApiResponse(responseCode = "404", description = "Producto o categoría no encontrado"),
            @ApiResponse(responseCode = "409", description = "Ya existe un producto con ese SKU")
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

    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir imagen del producto",
            description = "Sube la imagen del producto (campo multipart 'file'). Solo se aceptan JPG y PNG, máximo 5MB. Si ya tenía una, la reemplaza y borra la anterior del bucket")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen subida; devuelve el producto con su imagenUrl"),
            @ApiResponse(responseCode = "400", description = "El archivo no es una imagen válida o el formato no está permitido (solo JPG y PNG)"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "413", description = "La imagen supera el tamaño máximo permitido")
    })
    public ResponseEntity<ProductoAdminResponse> subirImagen(
            @Parameter(description = "ID del producto", example = "1") @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(productoService.subirImagen(id, file));
    }

    @DeleteMapping("/{id}/imagen")
    @Operation(summary = "Quitar imagen del producto",
            description = "Elimina la imagen del producto del bucket sin borrar el producto. Si no tenía, no hace nada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen eliminada; devuelve el producto sin imagenUrl"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<ProductoAdminResponse> quitarImagen(
            @Parameter(description = "ID del producto", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productoService.quitarImagen(id));
    }
}