package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.ProductRequest;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.models.Category;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.repository.VirtualCodeRepository.ProductStock;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public final class ProductFakes {

    private static final Faker faker = new Faker();

    private ProductFakes() {}

    public static Product fakeProduct() {
        return fakeProduct(product -> {});
    }

    public static Product fakeProduct(Consumer<Product> changes) {
        Category category = new Category();
        category.setId(faker.number().numberBetween(1L, 100_000L));
        category.setName("VideoGames");

        Product product = new Product();
        product.setId(faker.number().numberBetween(1L, 100_000L));
        product.setSku("STEAM-MX-200");
        product.setName("Steam Gift Card $200 MXN");
        product.setSlug("steam-gift-card-200-mxn");
        product.setDescription("Steam store gift card");
        product.setPlatform("Steam");
        product.setType(Product.ProductType.GIFT_CARD);
        product.setRegion("MX");
        product.setFaceValue(new BigDecimal("200.00"));
        product.setCurrency("MXN");
        product.setSalePrice(new BigDecimal("210.00"));
        product.setCostPrice(new BigDecimal("190.00"));
        product.setRedemptionInstructions("Redeem the code in Steam > Redeem a Steam Gift Card");
        product.setImageKey("products/abc.png");
        product.setActive(true);
        product.setCreatedAt(LocalDateTime.now());
        product.setCategory(category);

        changes.accept(product);
        return product;
    }

    public static class PublicProductBuilder {
        public Long id;
        public String slug;
        public String sku;
        public String name;
        public String description;
        public String platform;
        public Product.ProductType type;
        public String region;
        public BigDecimal faceValue;
        public String currency;
        public BigDecimal salePrice;
        public String redemptionInstructions;
        public String imageUrl;
        public Integer stock;
        public Long views = 0L;
        public Double averageRating = 0.0;
        public Long totalReviews = 0L;

        public PublicProductResponse build() {
            return new PublicProductResponse(id, slug, sku, name, description, platform, type,
                    region, faceValue, currency, salePrice, redemptionInstructions, imageUrl, stock,
                    views, averageRating, totalReviews);
        }
    }

    public static PublicProductResponse expectedPublicProduct(
            Product product, String imageUrl, int stock) {
        return expectedPublicProduct(product, imageUrl, stock, builder -> {});
    }

    public static PublicProductResponse expectedPublicProduct(
            Product product, String imageUrl, int stock, Consumer<PublicProductBuilder> changes) {
        PublicProductBuilder builder = new PublicProductBuilder();
        builder.id = product.getId();
        builder.slug = product.getSlug();
        builder.sku = product.getSku();
        builder.name = product.getName();
        builder.description = product.getDescription();
        builder.platform = product.getPlatform();
        builder.type = product.getType();
        builder.region = product.getRegion();
        builder.faceValue = product.getFaceValue();
        builder.currency = product.getCurrency();
        builder.salePrice = product.getSalePrice();
        builder.redemptionInstructions = product.getRedemptionInstructions();
        builder.imageUrl = imageUrl;
        builder.stock = stock;

        changes.accept(builder);
        return builder.build();
    }

    public static class ProductRequestBuilder {
        public String sku = "STEAM-MX-200";
        public String name = "Steam Gift Card $200 MXN";
        public String description = "Steam store gift card";
        public String platform = "Steam";
        public Product.ProductType type = Product.ProductType.GIFT_CARD;
        public String region = "MX";
        public BigDecimal faceValue = new BigDecimal("200.00");
        public String currency = "MXN";
        public BigDecimal salePrice = new BigDecimal("210.00");
        public BigDecimal costPrice = new BigDecimal("190.00");
        public String redemptionInstructions = "Redeem the code in Steam > Redeem a Steam Gift Card";
        public Boolean active = true;
        public Long categoryId;

        public ProductRequest build() {
            return new ProductRequest(sku, name, description, platform, type, region,
                    faceValue, currency, salePrice, costPrice, redemptionInstructions,
                    active, categoryId);
        }
    }

    public static ProductRequest fakeProductRequest() {
        return fakeProductRequest(builder -> {});
    }

    public static ProductRequest fakeProductRequest(Consumer<ProductRequestBuilder> changes) {
        ProductRequestBuilder builder = new ProductRequestBuilder();
        changes.accept(builder);
        return builder.build();
    }

    public static ProductStock fakeProductStock(Long productId, Long total) {
        return new ProductStock() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }
}
