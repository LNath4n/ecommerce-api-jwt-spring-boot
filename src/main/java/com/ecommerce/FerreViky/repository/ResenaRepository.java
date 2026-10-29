package com.ecommerce.FerreViky.repository;
import com.ecommerce.FerreViky.models.Resena;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ResenaRepository extends JpaRepository<Resena, Long> {

    Page<Resena> findByProductoId(Long productoId, Pageable pageable);

    boolean existsByProductoIdAndClienteId(Long productoId, Long clienteId);

    @Query("SELECT COALESCE(AVG(r.calificacion), 0) FROM Resena r WHERE r.producto.id = :id")
    Double promedioPorProducto(@Param("id") Long id);

    long countByProductoId(Long productoId);
}