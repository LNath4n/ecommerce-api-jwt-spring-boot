package com.ecommerce.FerreViky.mapper.product;

import static com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import static com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.AdminProductResponse;
import static com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.ProductRequest;

import com.ecommerce.FerreViky.models.Category;
import com.ecommerce.FerreViky.models.Product;

public class ProductMapper {

    public static PublicProductResponse toPublicResponse(
            Product product, String imageUrl, int stock) {
        return new PublicProductResponse(
                product.getId(),
                product.getSlug(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPlatform(),
                product.getType(),
                product.getRegion(),
                product.getFaceValue(),
                product.getCurrency(),
                product.getSalePrice(),
                product.getRedemptionInstructions(),
                imageUrl,
                stock,
                product.getViews(),
                product.getAverageRating(),
                product.getTotalReviews()
        );
    }

    public static AdminProductResponse toAdminResponse(
            Product product, String imageUrl, int stock) {
        return new AdminProductResponse(
                product.getId(),
                product.getSku(),
                product.getSlug(),
                product.getName(),
                product.getDescription(),
                product.getPlatform(),
                product.getType(),
                product.getRegion(),
                product.getFaceValue(),
                product.getCurrency(),
                product.getSalePrice(),
                product.getCostPrice(),
                product.getRedemptionInstructions(),
                product.getActive(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                imageUrl,
                stock,
                product.getCreatedAt(),
                product.getViews(),
                product.getAverageRating(),
                product.getTotalReviews()
        );
    }

    public static Product toEntity(ProductRequest request, Category category) {
        return updateEntity(new Product(), request, category);
    }

    public static Product updateEntity(
            Product product, ProductRequest request, Category category) {
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPlatform(request.platform());
        product.setType(request.type());
        product.setRegion(request.region());
        product.setFaceValue(request.faceValue());
        product.setCurrency(request.currency());
        product.setSalePrice(request.salePrice());
        product.setCostPrice(request.costPrice());
        product.setRedemptionInstructions(request.redemptionInstructions());

        if (request.active() != null) {
            product.setActive(request.active());
        }

        product.setCategory(category);
        return product;
    }
}