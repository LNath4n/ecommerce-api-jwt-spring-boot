package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerLoginRequest;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.CartCreationException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.CustomerNotFoundException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.EmailAlreadyExistsException;
import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.mapper.customer.CustomerMapper;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.CustomerRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Customer authentication service for sign-in and registration.
 * <p>
 * Both operations return a JWT for use in subsequent requests.
 */
@Service
@AllArgsConstructor
public class AuthService {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final CustomerMapper customerMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * Authenticates a customer with their credentials and generates a JWT.
     * <p>
     * The {@link AuthenticationManager} validates the credentials and throws an
     * exception if they are invalid. The email is normalized before authentication.
     *
     * @param dto customer's email and password
     * @return an {@link AuthResponse} containing the generated JWT
     * @throws org.springframework.security.core.AuthenticationException if the credentials are invalid
     * @throws CustomerNotFoundException if the customer cannot be found after authentication
     */
    public AuthResponse login(CustomerLoginRequest dto) {
        String email = normalizeEmail(dto.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, dto.password())
        );

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(()-> new CustomerNotFoundException(dto.email()));

        return new AuthResponse(jwtService.getToken(customer));
    }

    /**
     * Registers a new customer and creates an empty cart in the same transaction.
     * <p>
     * The process:
     * <ol>
     *   <li>Checks that the email is not already registered.</li>
     *   <li>Saves the customer with an encoded password.</li>
     *   <li>Creates and saves their {@link Cart}.</li>
     *   <li>Generates a JWT so they can use the API without signing in again.</li>
     * </ol>
     * If any step fails, the entire transaction is rolled back.
     *
     * @param dto new customer's email and password
     * @return an {@link AuthResponse} containing the new customer's JWT
     * @throws EmailAlreadyExistsException if the email is already registered
     * @throws CartCreationException if cart creation fails
     */
    @Transactional
    public AuthResponse registerCustomer(CustomerLoginRequest dto) {
        String email = normalizeEmail(dto.email());

        if (customerRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Customer customer = persistCustomer(dto, email);
        createCart(customer);

        return new AuthResponse(jwtService.getToken(customer));
    }

    /**
     * Builds and saves a customer with an encoded password.
     * <p>
     * If two registrations with the same email arrive concurrently, the second
     * one fails the unique constraint and is reported as a duplicate email.
     *
     * @param dto customer details
     * @param email normalized email
     * @return the saved customer
     * @throws EmailAlreadyExistsException if the database rejects the email as a duplicate
     */
    private Customer persistCustomer(CustomerLoginRequest dto, String email) {
        Customer customer = customerMapper.fromLoginRequest(dto);
        customer.setEmail(email);
        customer.setPassword(passwordEncoder.encode(dto.password()));

        try {
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    /**
     * Creates and saves an empty cart for the customer.
     *
     * @param customer customer who owns the cart
     * @throws CartCreationException if saving the cart fails
     */
    private void createCart(Customer customer) {
        Cart cart = new Cart();
        cart.setCustomer(customer);
        cart.setCreatedAt(LocalDateTime.now());

        try {
            cartRepository.saveAndFlush(cart);
        } catch (Exception e) {
            throw new CartCreationException(customer.getId(), e);
        }
    }

    /**
     * Trims whitespace and converts the email to lowercase for consistent comparisons.
     *
     * @param email input email, which may be {@code null}
     * @return the normalized email, or {@code null} if the input was {@code null}
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}