package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findBySku(String sku);

    Page<Producto> findByActivoTrue(Pageable pageable);
    Optional<Producto> findBySlug(String slug);
    boolean existsBySlug(String slug);

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    @Query("""
            SELECT DISTINCT p.plataforma
            FROM Producto p
            WHERE p.plataforma IS NOT NULL AND p.plataforma <> ''
            ORDER BY p.plataforma
            """)
    List<String> findPlataformasDistintas();
}