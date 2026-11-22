package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerCreatedResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerLoginRequest;
import com.ecommerce.FerreViky.exceptions.cart.CartExceptions;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.*;
import com.ecommerce.FerreViky.mapper.customer.CustomerMapper;
import com.ecommerce.FerreViky.models.Cart;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.repository.CartRepository;
import com.ecommerce.FerreViky.repository.CustomerRepository;
import com.ecommerce.FerreViky.utils.EmailLanguage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Unit tests")
class AuthServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private CustomerMapper customerMappers;
    @Mock private CartRepository cartRepository;
    @Mock private VerificationTokenService verificationTokenService;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    // Helper

    private Customer customer(Long id, String email, boolean verified) {
        Customer c = new Customer();
        c.setId(id);
        c.setEmail(email);
        c.setActive(true);
        c.setEmailVerified(verified);
        return c;
    }

    // login()

    @Test
    @DisplayName("When logging in with valid credentials and a verified email, it returns an AuthResponse with the token")
    void login_validCredentials_returnsToken() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("tilin@gmail.com", "secreto123");
        Customer customer = customer(1L, dto.email(), true);

        when(customerRepository.findByEmail(dto.email())).thenReturn(Optional.of(customer));
        when(jwtService.getToken(customer)).thenReturn("jwt-token-fake");

        // When
        AuthResponse response = authService.login(dto);

        // Then
        assertNotNull(response);
        assertEquals("jwt-token-fake", response.token());

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
        );
        verify(customerRepository).findByEmail(dto.email());
        verify(jwtService).getToken(customer);
    }

    @Test
    @DisplayName("When logging in with invalid credentials, it throws BadCredentialsException and does not query the repository")
    void login_invalidCredentials_throwsBadCredentials() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("tilin@gmail.com", "passwordMal");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        // When / Then
        assertThrows(BadCredentialsException.class, () -> authService.login(dto));

        verify(customerRepository, never()).findByEmail(any());
        verify(jwtService, never()).getToken(any());
    }

    @Test
    @DisplayName("When logging in with valid credentials but the customer is not in the repository, it throws CustomerNotFoundException and does not generate a token")
    void login_customerNotFound_throwsCustomerNotFound() {
        // Arrange
        CustomerLoginRequest dto = new CustomerLoginRequest("tilin@gmail.com", "secreto123");
        when(customerRepository.findByEmail(dto.email())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CustomerNotFoundException.class, () -> authService.login(dto));
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("When logging in with valid credentials but an unverified email, it throws EmailNotVerifiedException and does not generate a token")
    void login_emailNotVerified_throwsEmailNotVerified() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("tilin@gmail.com", "secreto123");
        when(customerRepository.findByEmail(dto.email()))
                .thenReturn(Optional.of(customer(1L, dto.email(), false)));

        // When / Then
        assertThrows(EmailNotVerifiedException.class, () -> authService.login(dto));
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("When logging in, the email is normalized (trimmed and lowercase) before authenticating and querying")
    void login_emailWithSpacesAndUppercase_isNormalized() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("  Tilin@Gmail.COM ", "secreto123");
        Customer customer = customer(1L, "tilin@gmail.com", true);

        when(customerRepository.findByEmail("tilin@gmail.com")).thenReturn(Optional.of(customer));
        when(jwtService.getToken(customer)).thenReturn("jwt-token-fake");

        // When
        AuthResponse response = authService.login(dto);

        // Then
        assertEquals("jwt-token-fake", response.token());
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("tilin@gmail.com", "secreto123")
        );
        verify(customerRepository).findByEmail("tilin@gmail.com");
    }

    // registerCustomer()

    @Test
    @DisplayName("When registering a customer with a new email, it is saved with their cart, a verification email is sent and no token is returned")
    void registerCustomer_emailNotRegistered_savesCustomerAndCartAndSendsEmail() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("nuevo@mail.com", "pass123");

        Customer customerSinId = new Customer();
        customerSinId.setEmail(dto.email());

        Customer customerConId = new Customer();
        customerConId.setId(99L);
        customerConId.setEmail(dto.email());

        CustomerCreatedResponse expected = new CustomerCreatedResponse(dto.email());

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customerSinId);
        when(passwordEncoder.encode(dto.password())).thenReturn("encoded-pass");
        when(customerRepository.saveAndFlush(customerSinId)).thenReturn(customerConId);
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerMappers.toCustomerCreatedResponse(customerConId)).thenReturn(expected);
        when(verificationTokenService.create(99L)).thenReturn("raw-token");   // <- aquí, antes del When

        // When
        CustomerCreatedResponse response = authService.registerCustomer(dto, EmailLanguage.ES);

        // Then
        assertEquals(expected, response);

        verify(passwordEncoder).encode(dto.password());
        verify(customerRepository).saveAndFlush(customerSinId);

        ArgumentCaptor<Cart> carritoCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).saveAndFlush(carritoCaptor.capture());
        Cart cartGuardado = carritoCaptor.getValue();
        assertEquals(customerConId, cartGuardado.getCustomer());
        assertNotNull(cartGuardado.getCreatedAt());

        verify(emailService).sendVerificationEmail(dto.email(), "raw-token", EmailLanguage.ES);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("When registering a customer with an email that already exists, it throws EmailAlreadyExistsException and saves nothing")
    void registerCustomer_emailAlreadyExists_throwsEmailAlreadyExists() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("existente@mail.com", "cualquierpass");

        when(customerRepository.existsByEmail(dto.email())).thenReturn(true);

        // When / Then
        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.registerCustomer(dto, EmailLanguage.EN));

        verify(customerRepository).existsByEmail(dto.email());
        verify(customerMappers, never()).fromLoginRequest(any());
        verify(customerRepository, never()).save(any());
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(verificationTokenService, emailService);
    }

    @Test
    @DisplayName("When registering a customer and saving the cart fails, it throws CartCreationException and sends no email")
    void registerCustomer_cartSaveFails_throwsCartCreationError() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("nuevo@mail.com", "pass");

        Customer customerSinId = new Customer();
        customerSinId.setEmail(dto.email());

        Customer customerConId = new Customer();
        customerConId.setId(1L);

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customerSinId);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(customerRepository.saveAndFlush(customerSinId)).thenReturn(customerConId);
        when(cartRepository.saveAndFlush(any(Cart.class)))
                .thenThrow(new RuntimeException("Error de BD"));

        // When / Then
        assertThrows(CartExceptions.CartCreationException.class,
                () -> authService.registerCustomer(dto, EmailLanguage.EN));

        verifyNoInteractions(verificationTokenService, emailService, jwtService);
    }

    @Test
    @DisplayName("When registering a customer, the password is stored encoded and never in plain text")
    void registerCustomer_storesEncodedPassword() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("nuevo@mail.com", "pass123");
        Customer customer = new Customer();

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customer);
        when(passwordEncoder.encode("pass123")).thenReturn("HASH");
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        // When
        authService.registerCustomer(dto, EmailLanguage.EN);

        // Then
        assertEquals("HASH", customer.getPassword());
        assertNotEquals("pass123", customer.getPassword());
    }

    @Test
    @DisplayName("When registering a customer, the account starts with the email unverified")
    void registerCustomer_startsUnverified() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("nuevo@mail.com", "pass123");
        Customer customer = new Customer();

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customer);
        when(passwordEncoder.encode("pass123")).thenReturn("HASH");
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        // When
        authService.registerCustomer(dto, EmailLanguage.EN);

        // Then
        assertFalse(customer.isEmailVerified());
    }

    @Test
    @DisplayName("When registering a customer, the email is normalized before checking and saving it")
    void registerCustomer_emailWithSpacesAndUppercase_isNormalized() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("  Nuevo@Mail.COM ", "pass123");
        Customer customer = new Customer();

        when(customerRepository.existsByEmail("nuevo@mail.com")).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customer);
        when(passwordEncoder.encode("pass123")).thenReturn("HASH");
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        // When
        authService.registerCustomer(dto, EmailLanguage.EN);

        // Then
        assertEquals("nuevo@mail.com", customer.getEmail());
        verify(customerRepository).existsByEmail("nuevo@mail.com");
    }

    @Test
    @DisplayName("When registering an email that exists with different casing, it throws EmailAlreadyExistsException")
    void registerCustomer_existingEmailWithDifferentCase_throwsEmailAlreadyExists() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("Existente@MAIL.com", "pass123");
        when(customerRepository.existsByEmail("existente@mail.com")).thenReturn(true);

        // When / Then
        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.registerCustomer(dto, EmailLanguage.EN));
        verifyNoInteractions(cartRepository, jwtService, verificationTokenService, emailService);
    }

    @Test
    @DisplayName("When registering and the database rejects the email as a duplicate, it throws EmailAlreadyExistsException without creating a cart or sending an email")
    void registerCustomer_dataIntegrity_throwsEmailAlreadyExists() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("nuevo@mail.com", "pass123");
        Customer customer = new Customer();

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customer);
        when(passwordEncoder.encode(dto.password())).thenReturn("HASH");
        when(customerRepository.saveAndFlush(customer))
                .thenThrow(new DataIntegrityViolationException("unique email"));

        // When / Then
        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.registerCustomer(dto, EmailLanguage.EN));
        verifyNoInteractions(cartRepository, jwtService, verificationTokenService, emailService);
    }

    @Test
    @DisplayName("When registering a customer, the steps run in order: check email, save customer, create cart, create token, send email")
    void registerCustomer_executesStepsInOrder() {
        // Given
        CustomerLoginRequest dto = new CustomerLoginRequest("nuevo@mail.com", "pass123");
        Customer customer = new Customer();
        customer.setId(5L);

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.fromLoginRequest(dto)).thenReturn(customer);
        when(passwordEncoder.encode(dto.password())).thenReturn("HASH");
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);
        when(verificationTokenService.create(5L)).thenReturn("raw-token");

        // When
        authService.registerCustomer(dto, EmailLanguage.EN);

        // Then
        InOrder orden = inOrder(customerRepository, cartRepository, verificationTokenService, emailService);
        orden.verify(customerRepository).existsByEmail(dto.email());
        orden.verify(customerRepository).saveAndFlush(customer);
        orden.verify(cartRepository).saveAndFlush(any(Cart.class));
        orden.verify(verificationTokenService).create(5L);
        orden.verify(emailService).sendVerificationEmail(dto.email(), "raw-token", EmailLanguage.EN);
    }

    // verifyEmail()

    @Test
    @DisplayName("When verifying with a valid token, the customer's email is marked as verified")
    void verifyEmail_validToken_marksEmailVerified() {
        // Given
        Customer customer = customer(7L, "tilin@gmail.com", false);
        when(verificationTokenService.consume("raw-token")).thenReturn(7L);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));

        // When
        authService.verifyEmail("raw-token");

        // Then
        assertTrue(customer.isEmailVerified());
    }

    @Test
    @DisplayName("When verifying with an invalid or expired token, it throws InvalidVerificationTokenException and does not touch the customer")
    void verifyEmail_invalidToken_throwsInvalidToken() {
        // Given
        when(verificationTokenService.consume("bad-token"))
                .thenThrow(new InvalidVerificationTokenException());

        // When / Then
        assertThrows(InvalidVerificationTokenException.class, () -> authService.verifyEmail("bad-token"));
        verifyNoInteractions(customerRepository);
    }

    @Test
    @DisplayName("When verifying and the token's customer no longer exists, it throws InvalidVerificationTokenException")
    void verifyEmail_customerDeleted_throwsInvalidToken() {
        // Given
        when(verificationTokenService.consume("raw-token")).thenReturn(7L);
        when(customerRepository.findById(7L)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(InvalidVerificationTokenException.class, () -> authService.verifyEmail("raw-token"));
    }

    // resendVerification()

    @Test
    @DisplayName("When resending to an active, unverified account, it creates a new token and sends the email in the requested language")
    void resendVerification_unverifiedAccount_sendsEmail() {
        // Given
        Customer customer = customer(3L, "tilin@gmail.com", false);
        when(customerRepository.findByEmail("tilin@gmail.com")).thenReturn(Optional.of(customer));
        when(verificationTokenService.create(3L)).thenReturn("raw-token");

        // When
        authService.resendVerification("  Tilin@Gmail.com ", EmailLanguage.ES);

        // Then
        verify(emailService).sendVerificationEmail("tilin@gmail.com", "raw-token", EmailLanguage.ES);
    }

    @Test
    @DisplayName("When resending to an already verified account, it sends nothing")
    void resendVerification_alreadyVerified_sendsNothing() {
        // Given
        when(customerRepository.findByEmail("tilin@gmail.com"))
                .thenReturn(Optional.of(customer(3L, "tilin@gmail.com", true)));

        // When
        authService.resendVerification("tilin@gmail.com", EmailLanguage.EN);

        // Then
        verifyNoInteractions(verificationTokenService, emailService);
    }

    @Test
    @DisplayName("When resending to an unknown email, it sends nothing and does not throw")
    void resendVerification_unknownEmail_sendsNothing() {
        // Given
        when(customerRepository.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());

        // When / Then
        assertDoesNotThrow(() -> authService.resendVerification("nadie@gmail.com", EmailLanguage.EN));
        verifyNoInteractions(verificationTokenService, emailService);
    }

    @Test
    @DisplayName("When resending to a deactivated account, it sends nothing")
    void resendVerification_inactiveAccount_sendsNothing() {
        // Given
        Customer customer = customer(3L, "tilin@gmail.com", false);
        customer.setActive(false);
        when(customerRepository.findByEmail("tilin@gmail.com")).thenReturn(Optional.of(customer));

        // When
        authService.resendVerification("tilin@gmail.com", EmailLanguage.EN);

        // Then
        verifyNoInteractions(verificationTokenService, emailService);
    }
}