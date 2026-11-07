package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart,Long>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cart FROM Cart cart WHERE cart.customer = :customer")
    Optional<Cart> findByCustomerForUpdate(@Param("customer") Customer customer);

    Optional<Cart> findByCustomer(Customer customer);

    Optional<Cart> findByCustomerId(Long customerId);

    Optional<Cart> findById(Long id);

    @Query("SELECT cart FROM Cart cart LEFT JOIN FETCH cart.items WHERE cart.customer = :customer")
    Optional<Cart> findByCustomerWithItems(@Param("customer") Customer customer);
}
