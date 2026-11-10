
package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewResponse;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewSummary;
import com.ecommerce.FerreViky.dto.validation.NoNulls.NoNullChars;
import com.ecommerce.FerreViky.service.ProductService;
import com.ecommerce.FerreViky.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@Validated
@CrossOrigin("*")
@RequestMapping("/products")
@Tag(name = "Products", description = "Browse the virtual codes catalog")
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "List products", description = "Returns active catalog products in paginated form, including their available code stock")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product list retrieved successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "A pagination parameter has an invalid format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidPage",
                                    summary = "Page must be an integer",
                                    value = "{\"status\":400,\"message\":\"Parameter 'page' has an invalid format\"}"
                            )
                    )
            )
    })
    public Page<PublicProductResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "none") String orden
            ) {
        return productService.getAllProducts(buildSafePageRequest(page, size,orden));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns an active product by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Product ID has an invalid format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidProductId",
                                    summary = "Product ID must be an integer",
                                    value = "{\"status\":400,\"message\":\"Parameter 'id' has an invalid format\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist or is inactive",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            )
    })
    public ResponseEntity<PublicProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/{id}/reviews")
    @Operation(
            summary = "List product reviews",
            description = "Returns public reviews in reverse chronological order. Page numbers start at 0; page size is limited to 100."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reviews retrieved"),
            @ApiResponse(
                    responseCode = "400",
                    description = "A path or pagination parameter has an invalid format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Parameter 'page' has an invalid format\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 7")
                    )
            )
    })
    public Page<ReviewResponse> getProductReviews(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable("id") Long productId,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of reviews per page (maximum 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, Math.min(page, 10_000));
        int safeSize = Math.max(1, Math.min(size, 100));
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return reviewService.list(productId, pageable);
    }

    @GetMapping("/{id}/reviews/summary")
    @Operation(
            summary = "Get product review summary",
            description = "Returns the average rating, rounded to one decimal place, and the total number of reviews"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review summary retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewSummary.class),
                            examples = @ExampleObject(value = "{\"productId\":7,\"average\":4.5,\"count\":12}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Product ID has an invalid format",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Parameter 'id' has an invalid format\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 7")
                    )
            )
    })
    public ReviewSummary getProductReviewSummary(
            @Parameter(description = "Product ID", example = "7")
            @PathVariable("id") Long productId) {
        return reviewService.summary(productId);
    }

    @GetMapping("/platforms")
    @Operation(summary = "List platforms", description = "Returns the distinct platforms in the catalog (Steam, Netflix, etc.), sorted alphabetically")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Platform list retrieved successfully")})
    public List<String> getPlatforms() {
        return productService.getPlatforms();
    }

    @GetMapping("/categories")
    @Operation(summary = "List categories", description = "Returns all categories without pagination, with only their ID and name")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Category list retrieved successfully")})
    public List<CategoryOptionResponse> getCategories() {
        return productService.getCategories();
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get product by slug", description = "Returns a catalog product by its slug")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Slug contains unsupported control characters",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidSlug",
                                    summary = "Path parameter contains a control character",
                                    value = "{\"status\":400,\"message\":\"Contiene caracteres de control no permitidos\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist or is inactive",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with slug: producto-inexistente")
                    )
            )
    })
    public ResponseEntity<PublicProductResponse> getProductBySlug(@NoNullChars @PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products", description = "Searches by free text in the name, SKU, platform, and description. Platform and category are optional filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "A numeric query parameter has an invalid format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidCategoryId",
                                    summary = "Category ID must be an integer",
                                    value = "{\"status\":400,\"message\":\"Parameter 'categoriaId' has an invalid format\"}"
                            )
                    )
            )
    })
    public Page<PublicProductResponse> searchProducts(
            @RequestParam(required = false) @NoNullChars String q,
            @RequestParam(required = false) @NoNullChars String plataforma,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "relevancia") String orden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.searchProducts(q, plataforma, categoriaId, buildSafePageRequest(page, size, orden));
    }

    @GetMapping("/top/views")
    @Operation(summary = "List most viewed products", description = "Returns the products with the highest number of views")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Product list retrieved successfully")})
    public List<PublicProductResponse> getMostViewedProducts() {
        return productService.getTopViewedProducts();
    }

    @GetMapping("/top/rated")
    @Operation(summary = "List highest-rated products", description = "Returns the products with the highest ratings")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Product list retrieved successfully")})
    public List<PublicProductResponse> getTopRatedProducts() {
        return productService.getTopRatedProducts();
    }

    @GetMapping("/top/reviewed")
    @Operation(summary = "List most-reviewed products", description = "Returns the products with the highest number of reviews")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Product list retrieved successfully")})
    public List<PublicProductResponse> getMostReviewedProducts() {
        return productService.getTopReviewedProducts();
    }

    private PageRequest buildSafePageRequest(int page, int size, String orden) {
        int safePage = Math.max(0, Math.min(page, 10_000));
        int safeSize = Math.max(1, Math.min(size, 100));
        Sort sort = switch (orden) {
            case "calificacion" -> Sort.by(Sort.Order.desc("averageRating"), Sort.Order.desc("totalReviews"));
            case "vistas"       -> Sort.by(Sort.Order.desc("views"));
            case "resenas"      -> Sort.by(Sort.Order.desc("totalReviews"));
            case "precio_asc"   -> Sort.by(Sort.Order.asc("salePrice"));
            case "precio_desc"  -> Sort.by(Sort.Order.desc("salePrice"));
            default             -> Sort.unsorted();
        };
        return PageRequest.of(safePage, safeSize, sort);
    }

}
