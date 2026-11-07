package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.AgregarCarrito;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.ActualizarCantidad;
import com.ecommerce.FerreViky.dto.dtos.carrito.CarritoDTO.CarritoResponseDTO;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.service.CartService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@AllArgsConstructor
@CrossOrigin("*")
@Validated
@Tag(name = "Cart", description = "Operations for adding items to and viewing the product cart")
@SecurityRequirement(name = "bearerAuth") // All endpoints in this controller require a JWT.
public class CartController {

    private final CartService cartService;

    @Operation(
            summary = "Add or update a product in the cart",
            description = "Adds a product to the authenticated customer's cart. " +
                    "If the product is already in the cart, the specified quantity is added. " +
                    "Returns an error if the total quantity exceeds the available stock."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product added or updated successfully",
                    content = @Content(schema = @Schema(type = "string", example = "Product added to cart"))),
            @ApiResponse(responseCode = "400", description = "Invalid quantity (≤ 0) or quantity exceeds available stock", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    })
    @PostMapping
    public ResponseEntity<String> addToCart(
            @Valid @RequestBody AgregarCarrito addRequest,
            @AuthenticationPrincipal Cliente customer) {
        cartService.addOrUpdateCartItem(addRequest, customer);
        return ResponseEntity.ok("Product added to cart");
    }

    @Operation(
            summary = "Get the authenticated customer's cart",
            description = "Returns the authenticated customer's entire cart, including all products and their quantities."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart retrieved successfully",
                    content = @Content(schema = @Schema(implementation = CarritoResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "The customer does not have a registered cart", content = @Content)
    })
    @GetMapping
    public ResponseEntity<CarritoResponseDTO> getCart(
            @AuthenticationPrincipal Cliente customer) {
        return ResponseEntity.ok(cartService.getCartByCustomer(customer));
    }

    @Operation(
            summary = "Update a product's cart quantity",
            description = "Sets the exact quantity of a product already in the authenticated customer's cart " +
                    "(replaces, rather than adds to, the current quantity). " +
                    "If the quantity is 0, the product is removed from the cart."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart updated successfully",
                    content = @Content(schema = @Schema(type = "string", example = "Cart updated with the requested quantities"))),
            @ApiResponse(responseCode = "400", description = "Invalid quantity (< 0) or quantity exceeds available stock", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "The customer has no cart or the product is not in it", content = @Content)
    })
    @PutMapping
    public ResponseEntity<String> updateCart(
            @Valid @RequestBody ActualizarCantidad quantityRequest,
            @AuthenticationPrincipal Cliente customer) {
        cartService.updateCartItemQuantity(quantityRequest, customer);
        return ResponseEntity.ok("Cart updated with the requested quantities");
    }

    @Operation(
            summary = "Clear the cart",
            description = "Removes all products from the authenticated customer's cart. " +
                    "The cart is kept but left empty."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart cleared successfully",
                    content = @Content(schema = @Schema(type = "string", example = "The entire cart was cleared"))),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "The customer does not have a registered cart", content = @Content)
    })
    @DeleteMapping
    public ResponseEntity<String> clearCart(
            @AuthenticationPrincipal Cliente customer) {
        cartService.clearCart(customer);
        return ResponseEntity.ok("The entire cart was cleared");
    }

    @Operation(
            summary = "Remove a product from the cart",
            description = "Completely removes a product from the authenticated customer's cart, " +
                    "regardless of its quantity."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product removed successfully",
                    content = @Content(schema = @Schema(type = "string", example = "Product removed"))),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "The customer has no cart or the product is not in it", content = @Content)
    })
    @DeleteMapping("/{productId}")
    public ResponseEntity<String> removeCartItem(
            @PathVariable Long productId,
            @AuthenticationPrincipal Cliente customer) {
        cartService.removeProductFromCart(productId, customer);
        return ResponseEntity.ok("Product with ID " + productId + " removed");
    }
}