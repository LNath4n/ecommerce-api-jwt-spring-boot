package com.ecommerce.FerreViky.repository;
import com.ecommerce.FerreViky.models.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Retrieves a product's reviews as a page.
    Page<Review> findByProductId(Long productId, Pageable pageable);

    // Checks whether a customer has reviewed a product.
    boolean existsByProductIdAndCustomerId(Long productId, Long customerId);

    // Calculates the average rating for a product.
    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.product.id = :productId")
    Double averageRatingByProductId(@Param("productId") Long productId);

    // Counts reviews for a product.
    long countByProductId(Long productId);
}