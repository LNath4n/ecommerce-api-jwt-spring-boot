package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.LoginClienteDto;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions.ErrorAlCrearCarritoException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ClienteNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.EmailYaExisteException;
import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.ClienteRepository;
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

    private final ClienteRepository clienteRepository;
    private final CarritoRepository carritoRepository;
    private final ClienteMappers clienteMappers;
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
     * @throws ClienteNoEncontradoException if the customer cannot be found after authentication
     */
    public AuthResponse login(LoginClienteDto dto) {
        String email = normalizeEmail(dto.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, dto.password())
        );

        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(()-> new ClienteNoEncontradoException(dto.email()));

        return new AuthResponse(jwtService.getToken(cliente));
    }

    /**
     * Registers a new customer and creates an empty cart in the same transaction.
     * <p>
     * The process:
     * <ol>
     *   <li>Checks that the email is not already registered.</li>
     *   <li>Saves the customer with an encoded password.</li>
     *   <li>Creates and saves their {@link Carrito}.</li>
     *   <li>Generates a JWT so they can use the API without signing in again.</li>
     * </ol>
     * If any step fails, the entire transaction is rolled back.
     *
     * @param dto new customer's email and password
     * @return an {@link AuthResponse} containing the new customer's JWT
     * @throws EmailYaExisteException if the email is already registered
     * @throws ErrorAlCrearCarritoException if cart creation fails
     */
    @Transactional
    public AuthResponse registerCustomer(LoginClienteDto dto) {
        String email = normalizeEmail(dto.email());

        if (clienteRepository.existsByEmail(email)) {
            throw new EmailYaExisteException(email);
        }

        Cliente cliente = persistCustomer(dto, email);
        createCart(cliente);

        return new AuthResponse(jwtService.getToken(cliente));
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
     * @throws EmailYaExisteException if the database rejects the email as a duplicate
     */
    private Cliente persistCustomer(LoginClienteDto dto, String email) {
        Cliente cliente = clienteMappers.DtoLoginACliente(dto);
        cliente.setEmail(email);
        cliente.setPassword(passwordEncoder.encode(dto.password()));

        try {
            return clienteRepository.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException e) {
            throw new EmailYaExisteException(email);
        }
    }

    /**
     * Creates and saves an empty cart for the customer.
     *
     * @param cliente customer who owns the cart
     * @throws ErrorAlCrearCarritoException if saving the cart fails
     */
    private void createCart(Cliente cliente) {
        Carrito carrito = new Carrito();
        carrito.setCliente(cliente);
        carrito.setFechaCreacion(LocalDateTime.now());

        try {
            carritoRepository.saveAndFlush(carrito);
        } catch (Exception e) {
            throw new ErrorAlCrearCarritoException(cliente.getId(), e);
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