package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Checks whether a customer with the given email already exists.
     *
     * <p>Used before saving a new customer to raise a clear domain exception
     * ({@link com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.EmailYaExisteException})
     * instead of relying on a generic JPA exception from the database's unique constraint.</p>
     *
     * @param email email address to check
     * @return {@code true} if the email is already registered
     */
    boolean existsByEmail(String email);

    /**
     * Finds a customer by email.
     *
     * <p>Returns an {@link Optional} instead of {@code null}, requiring callers
     * to explicitly handle the case where the customer is not found.</p>
     *
     * @param email customer's email address
     * @return an optional containing the customer, or empty if none exists
     */
    Optional<Customer> findByEmail(String email);

}