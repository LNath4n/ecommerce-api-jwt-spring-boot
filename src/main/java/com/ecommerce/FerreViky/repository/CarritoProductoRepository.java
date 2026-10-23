package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.ProductoEnCarrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarritoProductoRepository extends JpaRepository<ProductoEnCarrito, Long> {
    @Modifying
    @Query("DELETE FROM ProductoEnCarrito cp WHERE cp.producto.id = :productoId")
    void eliminarPorProductoId(@Param("productoId") Long productoId);
}