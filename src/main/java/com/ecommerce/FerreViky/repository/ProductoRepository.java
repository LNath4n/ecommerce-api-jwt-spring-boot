package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.dto.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.models.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findById(long id);

    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.grupoDeProductos")
    Page<Producto> findAllWithGrupo(Pageable pageable);

    @Query("""
        select distinct p.marca
        from Producto p
        where p.marca is not null and p.marca <> ''
        order by p.marca
        """)
    List<String> findMarcasDistintas();
}