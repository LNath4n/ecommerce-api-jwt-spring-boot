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

    long countByProductIdAndStatus(Long productId, CodeStatus status);

    interface ProductStock {
        Long getProductId();
        Long getTotal();
    }

    @Query("""
            SELECT c.product.id AS productId, COUNT(c) AS total
            FROM VirtualCode c
            WHERE c.product.id IN :productIds AND c.status = :status
            GROUP BY c.product.id
            """)
    List<ProductStock> findStockByProductIdsAndStatus(@Param("productIds") Collection<Long> productIds,
                                                      @Param("status") CodeStatus status);

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

    boolean existsByProductId(Long productId);

    @Query("select c.code from VirtualCode c where c.code in :codes")
    List<String> findExistingCodes(@Param("codes") Collection<String> codes);
}