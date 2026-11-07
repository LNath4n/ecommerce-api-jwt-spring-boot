
package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.dto.validation.NoNulls.NoNullChars;
import com.ecommerce.FerreViky.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping
    @Operation(summary = "List products", description = "Returns active catalog products in paginated form, including their available code stock")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Product list retrieved successfully")})
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
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<PublicProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
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
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<PublicProductResponse> getProductBySlug(@NoNullChars @PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products", description = "Searches by free text in the name, SKU, platform, and description. Platform and category are optional filters")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Search results retrieved successfully")})
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
