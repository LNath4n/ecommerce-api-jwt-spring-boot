package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Finds a product by its SKU.
    Optional<Product> findBySku(String sku);

    // Retrieves active products as a page.
    Page<Product> findByActiveTrue(Pageable pageable);

    // Finds a product by its slug.
    Optional<Product> findBySlug(String slug);

    // Checks whether a slug is already in use.
    boolean existsBySlug(String slug);

    // Checks whether an SKU is already in use.
    boolean existsBySku(String sku);

    // Checks whether another product uses the SKU.
    boolean existsBySkuAndIdNot(String sku, Long id);

    // Lists distinct non-empty product platforms.
    @Query("""
            SELECT DISTINCT p.platform
            FROM Product p
            WHERE p.platform IS NOT NULL AND p.platform <> ''
            ORDER BY p.platform
            """)
    List<String> findDistinctPlatforms();

    // Increments a product's view count.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Product p SET p.views = p.views + 1 WHERE p.id = :id")
    void incrementViews(@Param("id") Long id);

    // Checks whether a category is assigned to any product.
    boolean existsByCategoryId(Long categoryId);

    // Retrieves the ten most-viewed active products.
    List<Product> findTop10ByActiveTrueOrderByViewsDesc();

    // Retrieves the ten active products with the most reviews.
    List<Product> findTop10ByActiveTrueOrderByTotalReviewsDesc();

    // Retrieves the highest-rated active products meeting the review threshold.
    List<Product> findTop10ByActiveTrueAndTotalReviewsGreaterThanEqualOrderByAverageRatingDescTotalReviewsDesc(
            long minimumReviews);
}