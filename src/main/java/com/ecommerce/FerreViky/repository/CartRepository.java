package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    // Finds and locks a customer's cart for updates.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cart FROM Cart cart WHERE cart.customer = :customer")
    Optional<Cart> findByCustomerForUpdate(@Param("customer") Customer customer);

    // Finds a cart by its customer.
    Optional<Cart> findByCustomer(Customer customer);

    // Finds a cart by its customer's ID.
    Optional<Cart> findByCustomerId(Long customerId);

    // Finds a cart by its ID.
    Optional<Cart> findById(Long id);

    // Finds a customer's cart and fetches its items.
    @Query("SELECT cart FROM Cart cart LEFT JOIN FETCH cart.items WHERE cart.customer = :customer")
    Optional<Cart> findByCustomerWithItems(@Param("customer") Customer customer);
}
