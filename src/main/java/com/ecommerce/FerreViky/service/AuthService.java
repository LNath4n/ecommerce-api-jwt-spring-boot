package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerCreatedResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerLoginRequest;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions.CartCreationException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.CustomerNotFoundException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.EmailAlreadyExistsException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.EmailNotVerifiedException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.InvalidVerificationTokenException;
import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.mapper.customer.CustomerMapper;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.CustomerRepository;
import com.ecommerce.FerreViky.utils.EmailLanguage;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Customer authentication service: sign-in, registration and email verification.
 * <p>
 * Only {@link #login} returns a JWT. Registration creates the account in an
 * unverified state and sends a verification email; the customer must verify
 * their email before they can sign in.
 */
@Service
@AllArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final CustomerMapper customerMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;

    /**
     * Authenticates a customer with their credentials and generates a JWT.
     * <p>
     * The {@link AuthenticationManager} validates the credentials and throws an
     * exception if they are invalid. The email is normalized before authentication.
     * The verification status is checked only after the credentials are valid, so
     * the account state is not revealed to someone who does not know the password.
     *
     * @param dto customer's email and password
     * @return an {@link AuthResponse} containing the generated JWT
     * @throws org.springframework.security.core.AuthenticationException if the credentials are invalid
     * @throws CustomerNotFoundException if the customer cannot be found after authentication
     * @throws EmailNotVerifiedException if the customer has not verified their email yet
     */
    public AuthResponse login(CustomerLoginRequest dto) {
        String email = normalizeEmail(dto.email());

        log.debug("Login attempt received");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, dto.password())
            );
        } catch (AuthenticationException e) {
            log.warn("Login failed: {}", e.getClass().getSimpleName());
            throw e;
        }

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("Credentials were valid but the customer was not found in the database");
                    return new CustomerNotFoundException(dto.email());
                });

        if (!customer.isEmailVerified()) {
            log.warn("Login blocked: customer {} has not verified their email", customer.getId());
            throw new EmailNotVerifiedException(customer.getEmail());
        }

        log.info("Customer {} logged in", customer.getId());
        return new AuthResponse(jwtService.getToken(customer));
    }

    /**
     * Registers a new customer and creates an empty cart in the same transaction.
     * <p>
     * The process:
     * <ol>
     *   <li>Checks that the email is not already registered.</li>
     *   <li>Saves the customer with an encoded password and an unverified email.</li>
     *   <li>Creates and saves their {@link Cart}.</li>
     *   <li>Sends the verification email once the transaction commits.</li>
     * </ol>
     * If any step fails, the entire transaction is rolled back and no email is sent.
     * No JWT is returned: the customer must verify their email and then sign in.
     *
     * @param dto new customer's email and password
     * @return the new customer's ID and email
     * @throws EmailAlreadyExistsException if the email is already registered
     * @throws CartCreationException if cart creation fails
     */
    @Transactional
    public CustomerCreatedResponse registerCustomer(CustomerLoginRequest dto, EmailLanguage language) {
        String email = normalizeEmail(dto.email());

        log.debug("Registration attempt received");

        if (customerRepository.existsByEmail(email)) {
            log.warn("Registration rejected: email is already registered");
            throw new EmailAlreadyExistsException(email);
        }

        Customer customer = persistCustomer(dto, email);
        createCart(customer);
        sendVerificationAfterCommit(customer, language);

        log.info("Customer {} registered, pending email verification", customer.getId());
        return customerMapper.toCustomerCreatedResponse(customer);
    }

    /**
     * Confirms a customer's email using the token received by mail.
     * <p>
     * The token is single-use: it is deleted when consumed.
     *
     * @param rawToken token from the verification link
     * @throws InvalidVerificationTokenException if the token does not exist, has expired,
     *                                           or its customer no longer exists
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        Long customerId = verificationTokenService.consume(rawToken);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Verification failed: customer {} not found", customerId);
                    return new InvalidVerificationTokenException();
                });

        customer.setEmailVerified(true);
        log.info("Email verified for customer {}", customer.getId());
    }

    /**
     * Sends a new verification email if the account exists, is active and is not
     * verified yet.
     * <p>
     * Does nothing otherwise and never reveals whether the email is registered,
     * so the caller always gets the same response.
     *
     * @param rawEmail email address entered by the customer
     */
    public void resendVerification(String rawEmail, EmailLanguage language) {
        String email = normalizeEmail(rawEmail);

        customerRepository.findByEmail(email)
                .filter(c -> !c.isEmailVerified() && c.isActive())
                .ifPresentOrElse(
                        c -> sendVerificationAfterCommit(c, language),
                        () -> log.debug("Resend ignored: unknown, inactive or already verified account"));
    }

    /**
     * Creates the verification token and sends the email after the current
     * transaction commits, so no email goes out for a rolled-back registration.
     * Without an active transaction (resend), it sends right away.
     */
    private void sendVerificationAfterCommit(Customer customer, EmailLanguage language) {
        Runnable send = () -> {
            String rawToken = verificationTokenService.create(customer.getId());
            emailService.sendVerificationEmail(customer.getEmail(), rawToken, language);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }

        //Asi evitamos rollback aun si falla el email
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
            // The exception is not logged on purpose: its message can include the email
            log.warn("Registration rejected: duplicate email detected by the database (concurrent registration)");
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
            log.error("Cart creation failed for customer {}", customer.getId(), e);
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