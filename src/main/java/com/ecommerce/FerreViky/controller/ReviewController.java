package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewEligibility;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewRequest;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewResponse;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews/products/{productId}")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "Reviews", description = "Read and manage product reviews")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/mine")
    @Operation(
            summary = "Get my review",
            description = "Returns the authenticated customer's review for this product"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Product ID has an invalid format",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "The authenticated customer has no review for this product",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "No tienes una reseña para el producto 7")
                    )
            )
    })
    public ReviewResponse getMine(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable Long productId,
            @AuthenticationPrincipal Customer customer) {
        return reviewService.getMine(productId, customer);
    }

    @GetMapping("/eligibility")
    @Operation(
            summary = "Check review eligibility",
            description = "Indicates whether the authenticated customer has purchased the product and has not reviewed it yet"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review eligibility retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewEligibility.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Product ID has an invalid format",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content)
    })
    public ReviewEligibility eligibility(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable Long productId,
            @AuthenticationPrincipal Customer customer) {
        return reviewService.eligibility(productId, customer);
    }

    @PostMapping
    @Operation(
            summary = "Create a product review",
            description = "Creates a review for a product the authenticated customer has purchased and has not reviewed before. Rating must be 1–5; comment is optional and limited to 1000 characters."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Review created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Review fields are invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"rating\":\"must be less than or equal to 5\"}}"
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "403",
                    description = "The customer has no paid order containing this product",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Solo puedes reseñar productos que hayas comprado (producto 7)")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 7")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The customer has already reviewed this product",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Ya reseñaste el producto 7")
                    )
            )
    })
    public ResponseEntity<ReviewResponse> create(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable Long productId,
            @AuthenticationPrincipal Customer customer,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(productId, customer, request));
    }

    @PutMapping
    @Operation(
            summary = "Update my product review",
            description = "Updates the authenticated customer's review. Rating must be 1–5; comment is optional and limited to 1000 characters."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Review fields are invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"rating\":\"must be less than or equal to 5\"}}"
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "The authenticated customer has no review for this product",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "No tienes una reseña para el producto 7")
                    )
            )
    })
    public ReviewResponse update(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable Long productId,
            @AuthenticationPrincipal Customer customer,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(productId, customer, request);
    }

    @DeleteMapping
    @Operation(
            summary = "Delete my product review",
            description = "Deletes the authenticated customer's review for this product"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Review deleted"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Product ID has an invalid format",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(responseCode = "401", description = "JWT is missing or invalid", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "The authenticated customer has no review for this product",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "No tienes una reseña para el producto 7")
                    )
            )
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable Long productId,
            @AuthenticationPrincipal Customer customer) {
        reviewService.delete(productId, customer);
        return ResponseEntity.noContent().build();
    }
}
