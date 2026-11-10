package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.models.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByProductIdAndCustomerId(Long productId, Long customerId);

    boolean existsByProductIdAndCustomerId(Long productId, Long customerId);

    Page<Review> findByProductId(Long productId, Pageable pageable);

    long countByProductId(Long productId);
    @Query("select avg(r.rating) from Review r where r.product.id = :productId")
    Double findAverageRating(@Param("productId") Long productId);

    @Query("""
            select case when count(oi) > 0 then true else false end
            from OrderItem oi
            where oi.product.id = :productId
              and oi.orders.buyer.id = :customerId
              and oi.orders.status = :status
            """)
    boolean hasPurchased(@Param("productId") Long productId,
                         @Param("customerId") Long customerId,
                         @Param("status") OrderStatus status);
}