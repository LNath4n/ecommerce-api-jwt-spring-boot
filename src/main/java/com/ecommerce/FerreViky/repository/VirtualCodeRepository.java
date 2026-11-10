package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.VirtualCode;
import com.ecommerce.FerreViky.models.VirtualCode.CodeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VirtualCodeRepository extends JpaRepository<VirtualCode, Long> {

    // Counts codes for one product and status.
    long countByProductIdAndStatus(Long productId, CodeStatus status);

    interface ProductStock {
        // Returns the product ID.
        Long getProductId();

        // Returns the number of matching codes.
        Long getTotal();
    }

    // Returns stock counts grouped by product.
    @Query("""
            SELECT c.product.id AS productId, COUNT(c) AS total
            FROM VirtualCode c
            WHERE c.product.id IN :productIds AND c.status = :status
            GROUP BY c.product.id
            """)
    List<ProductStock> findStockByProductIdsAndStatus(@Param("productIds") Collection<Long> productIds,
                                                      @Param("status") CodeStatus status);

    // Loads matching codes with a write lock for reservation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM VirtualCode c
            WHERE c.product.id = :productId
              AND c.status = :status
            ORDER BY c.id
            """)
    List<VirtualCode> findByProductIdAndStatusForUpdate(@Param("productId") Long productId,
                                                        @Param("status") CodeStatus status,
                                                        Pageable pageable);

    // Checks whether a product has any virtual codes.
    boolean existsByProductId(Long productId);

    // Returns which of the supplied codes already exist.
    // Returns which of the supplied hashes already exist.
    @Query("select c.codeHash from VirtualCode c where c.codeHash in :hashes")
    List<String> findExistingHashes(@Param("hashes") Collection<String> hashes);
    List<VirtualCode> findByOrdersId(Long orderId);
}