package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.swing.text.html.Option;
import java.util.Optional;

public interface CarritoRepository extends JpaRepository<Carrito,Long>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Carrito c WHERE c.cliente = :cliente")
    Optional<Carrito> findByClienteParaActualizar(@Param("cliente") Cliente cliente);

    Optional<Carrito> findByCliente(Cliente cliente);


    Optional<Carrito> findByClienteId(Long clienteId);

    Optional<Carrito> findById(Long id);

    @Query("SELECT c FROM Carrito c LEFT JOIN FETCH c.productos WHERE c.cliente = :cliente")
    Optional<Carrito> findByClienteConProductos(@Param("cliente") Cliente cliente);
}
