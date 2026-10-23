package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.CodigoVirtual;
import com.ecommerce.FerreViky.models.CodigoVirtual.EstadoCodigo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CodigoVirtualRepository extends JpaRepository<CodigoVirtual, Long> {

    // Stock de UN producto
    long countByProductoIdAndEstado(Long productoId, EstadoCodigo estado);

    // Proyección para el stock de varios productos
    // Notaq Spring Data crea por debajo un objeto que la implementa y rellena los datos.
    interface StockProducto {
        Long getProductoId();
        Long getTotal();
    } //Debe haber un productoId y un Total en la Query,Si no estan bien dan error en RunTime

    // Stock de VARIOS productos en una sola consulta (evita N+1 en listados)
    @Query("""
            SELECT c.producto.id AS productoId, COUNT(c) AS total
            FROM CodigoVirtual c
            WHERE c.producto.id IN :ids AND c.estado = :estado
            GROUP BY c.producto.id
            """)
    List<StockProducto> contarPorProducto(@Param("ids") Collection<Long> ids,
                                          @Param("estado") EstadoCodigo estado);

    // Para tomar N códigos disponibles al momento de pagar/reservar
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM CodigoVirtual c
            WHERE c.producto.id = :productoId
              AND c.estado = :estado
            ORDER BY c.id
            """)
    List<CodigoVirtual> buscarParaReservar(@Param("productoId") Long productoId,
                                           @Param("estado") EstadoCodigo estado,
                                           Pageable pageable);

    boolean existsByProductoId(Long productoId);
}