package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.AddCartItemRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.UpdateCartItemQuantityRequest;
import com.ecommerce.FerreViky.dto.dtos.cart.CartDTO.CartResponse;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
            @ApiResponse(
                    responseCode = "400",
                    description = "The request quantity is invalid or the requested total exceeds available stock",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "InvalidQuantity",
                                            summary = "Quantity is below the allowed minimum",
                                            value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"quantity\":\"Quantity must be at least 1\"}}"
                                    )
                            ),
                            @Content(
                                    mediaType = "text/plain",
                                    schema = @Schema(type = "string"),
                                    examples = {
                                            @ExampleObject(
                                                    name = "InsufficientStock",
                                                    summary = "Requested units exceed available stock",
                                                    value = "Cannot add 2 units of Steam Gift Card $200 MXN; only 1 are in stock"
                                            ),
                                            @ExampleObject(
                                                    name = "CartQuantityExceedsStock",
                                                    summary = "Adding units would make the cart quantity exceed stock",
                                                    value = "You already have 1 of Steam Gift Card $200 MXN in your cart and only 1 are in stock. You can add 0 more"
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist or is inactive",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            )
    })
    @PostMapping
    public ResponseEntity<String> addToCart(
            @Valid @RequestBody AddCartItemRequest addRequest,
            @AuthenticationPrincipal Customer customer) {
        cartService.addOrUpdateCartItem(addRequest, customer);
        return ResponseEntity.ok("Product added to cart");
    }

    @Operation(
            summary = "Get the authenticated customer's cart",
            description = "Returns the authenticated customer's entire cart, including all products and their quantities."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart retrieved successfully",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer does not have a registered cart",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Cart not found: 42")
                    )
            )
    })
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal Customer customer) {
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
            @ApiResponse(
                    responseCode = "400",
                    description = "The request quantity is invalid or the requested quantity exceeds available stock",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "NegativeQuantity",
                                            summary = "Quantity is below the allowed minimum",
                                            value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"quantity\":\"Quantity cannot be negative\"}}"
                                    )
                            ),
                            @Content(
                                    mediaType = "text/plain",
                                    schema = @Schema(type = "string"),
                                    examples = @ExampleObject(
                                            name = "InsufficientStock",
                                            summary = "Requested quantity exceeds available stock",
                                            value = "Cannot have 3 units of Steam Gift Card $200 MXN; only 2 are in stock"
                                    )
                            )
                    }
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer has no cart or the product is not in it",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(
                                            name = "CartNotFound",
                                            summary = "Customer has no registered cart",
                                            value = "No cart found for customer 42"
                                    ),
                                    @ExampleObject(
                                            name = "ProductNotInCart",
                                            summary = "Product is not in the cart",
                                            value = "Product with ID 2 is not in the cart"
                                    )
                            }
                    )
            )
    })
    @PutMapping
    public ResponseEntity<String> updateCart(
            @Valid @RequestBody UpdateCartItemQuantityRequest quantityRequest,
            @AuthenticationPrincipal Customer customer) {
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
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer does not have a registered cart",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "No cart found for customer 42")
                    )
            )
    })
    @DeleteMapping
    public ResponseEntity<String> clearCart(
            @AuthenticationPrincipal Customer customer) {
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
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer has no cart or the product is not in it",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(
                                            name = "CartNotFound",
                                            summary = "Customer has no registered cart",
                                            value = "No cart found for customer 42"
                                    ),
                                    @ExampleObject(
                                            name = "ProductNotInCart",
                                            summary = "Product is not in the cart",
                                            value = "Product with ID 2 is not in the cart"
                                    )
                            }
                    )
            )
    })
    @DeleteMapping("/{productId}")
    public ResponseEntity<String> removeCartItem(
            @PathVariable Long productId,
            @AuthenticationPrincipal Customer customer) {
        cartService.removeProductFromCart(productId, customer);
        return ResponseEntity.ok("Product with ID " + productId + " removed");
    }
}