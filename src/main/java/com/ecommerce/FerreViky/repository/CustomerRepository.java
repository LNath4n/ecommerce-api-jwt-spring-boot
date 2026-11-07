package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Checks whether an email is already registered.
    boolean existsByEmail(String email);

    // Finds a customer by email.
    Optional<Customer> findByEmail(String email);

}