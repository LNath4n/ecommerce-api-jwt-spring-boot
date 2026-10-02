package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.service.CarritoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carrito")
@AllArgsConstructor
@CrossOrigin("*")
@Tag(name = "Carrito", description = "Operaciones de registro y consulta de carrito de productos")
@SecurityRequirement(name = "bearerAuth") // todos los endpoints de este controller requieren JWT
public class CarritoController {

    private final CarritoService carritoService;

    @Operation(
            summary = "Agregar o actualizar producto en el carrito",
            description = "Agrega un producto al carrito del cliente autenticado. " +
                    "Si el producto ya existe en el carrito, suma la cantidad indicada. " +
                    "Lanza error si la cantidad total supera el stock disponible."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto agregado o actualizado correctamente",
                    content = @Content(schema = @Schema(type = "string", example = "Producto agregado al carrito"))),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida (≤ 0) o excede el stock disponible", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    })
    @PostMapping
    public ResponseEntity<String> agregarCarrito(
            @Valid @RequestBody AgregarCarrito dto,
            @AuthenticationPrincipal Cliente cliente) {
        carritoService.agregarOActualizar(dto, cliente);
        return ResponseEntity.ok("Producto agregado al carrito");
    }

    @Operation(
            summary = "Obtener carrito del cliente autenticado",
            description = "Retorna el carrito completo del cliente autenticado, incluyendo todos los productos y sus cantidades."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carrito obtenido exitosamente",
                    content = @Content(schema = @Schema(implementation = CarritoResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "El cliente no tiene un carrito registrado", content = @Content)
    })
    @GetMapping
    public ResponseEntity<CarritoResponseDTO> obtenerCarrito(
            @AuthenticationPrincipal Cliente cliente) {
        return ResponseEntity.ok(carritoService.obtenerCarritoPorCliente(cliente));
    }

    @Operation(
            summary = "Actualizar la cantidad de un producto del carrito",
            description = "Establece la cantidad exacta de un producto que ya está en el carrito " +
                    "del cliente autenticado (reemplaza, no suma). " +
                    "Si la cantidad es 0, el producto se elimina del carrito."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carrito actualizado correctamente",
                    content = @Content(schema = @Schema(type = "string", example = "Carrito actualizado con las cantidades solicitadas"))),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida (< 0) o excede el stock disponible", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "El cliente no tiene carrito o el producto no está en él", content = @Content)
    })
    @PutMapping
    public ResponseEntity<String> actualizarCarrito(
            @Valid @RequestBody ActualizarCantidad dto,
            @AuthenticationPrincipal Cliente cliente) {
        carritoService.actualizarCantidad(dto, cliente);
        return ResponseEntity.ok("Carrito actualizado con las cantidades solicitadas");
    }

    @Operation(
            summary = "Eliminar un producto del carrito",
            description = "Quita por completo un producto del carrito del cliente autenticado, " +
                    "sin importar la cantidad que tuviera."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto eliminado correctamente",
                    content = @Content(schema = @Schema(type = "string", example = "Producto eliminado"))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "El cliente no tiene carrito o el producto no está en él", content = @Content)
    })
    @DeleteMapping("/{idProducto}")
    public ResponseEntity<String> borrarItem(
            @PathVariable Long idProducto,
            @AuthenticationPrincipal Cliente cliente) {
        carritoService.eliminarProducto(idProducto, cliente);
        return ResponseEntity.ok("Producto con id " + idProducto + "eliminado");
    }
}