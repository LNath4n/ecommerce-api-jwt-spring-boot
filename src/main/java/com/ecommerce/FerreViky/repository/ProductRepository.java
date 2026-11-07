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

    Optional<Product> findBySku(String sku);

    Page<Product> findByActiveTrue(Pageable pageable);
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    @Query("""
            SELECT DISTINCT p.platform
            FROM Product p
            WHERE p.platform IS NOT NULL AND p.platform <> ''
            ORDER BY p.platform
            """)
    List<String> findDistinctPlatforms();

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Product p SET p.views = p.views + 1 WHERE p.id = :id")
    void incrementViews(@Param("id") Long id);

    boolean existsByCategoryId(Long categoryId);

    List<Product> findTop10ByActiveTrueOrderByViewsDesc();

    List<Product> findTop10ByActiveTrueOrderByTotalReviewsDesc();

    // Highest-rated products with at least the specified number of reviews.
    List<Product> findTop10ByActiveTrueAndTotalReviewsGreaterThanEqualOrderByAverageRatingDescTotalReviewsDesc(
            long minimumReviews);
}