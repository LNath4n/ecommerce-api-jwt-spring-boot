package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.LoginClienteDto;
import com.ecommerce.FerreViky.exceptions.carrito.CarritoExceptions;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.*;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Carrito;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.CarritoRepository;
import com.ecommerce.FerreViky.repository.ClienteRepository;
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

    @Mock private ClienteRepository customerRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private ClienteMappers customerMappers;
    @Mock private CarritoRepository cartRepository;

    @InjectMocks
    private AuthService authService;

    // login()

    @Test
    @DisplayName("When logging in with valid credentials, it returns an AuthResponse with the token")
    void login_validCredentials_returnsToken() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("tilin@gmail.com", "secreto123");

        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setEmail(dto.email());

        when(customerRepository.findByEmail(dto.email())).thenReturn(Optional.of(cliente));
        when(jwtService.getToken(cliente)).thenReturn("jwt-token-fake");

        // When
        AuthResponse response = authService.login(dto);

        // Then
        assertNotNull(response);
        assertEquals("jwt-token-fake", response.token());

        // The AuthenticationManager must have been invoked with the correct credentials
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
        );
        verify(customerRepository).findByEmail(dto.email());
        verify(jwtService).getToken(cliente);
    }

    @Test
    @DisplayName("When logging in with invalid credentials, it throws BadCredentialsException and does not query the repository")
    void login_invalidCredentials_throwsBadCredentials() {
        // Given
        // The AuthenticationManager does the validation: if the credentials are wrong, it throws BadCredentialsException
        LoginClienteDto dto = new LoginClienteDto("tilin@gmail.com", "passwordMal");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        // When / Then
        assertThrows(BadCredentialsException.class, () -> authService.login(dto));

        // It must not reach the repository if authentication fails
        verify(customerRepository, never()).findByEmail(any());
        verify(jwtService, never()).getToken(any());
    }

    @Test
    @DisplayName("When logging in with valid credentials but the customer is not in the repository, it throws ClienteNoEncontradoException and does not generate a token")
    void login_customerNotFound_throwsCustomerNotFound() {
        // Arrange
        LoginClienteDto dto = new LoginClienteDto("tilin@gmail.com", "secreto123");
        when(customerRepository.findByEmail(dto.email())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ClienteNoEncontradoException.class, () -> authService.login(dto));
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(jwtService);
    }

    // registerCustomer()

    @Test
    @DisplayName("When registering a customer with a new email, it is saved along with their cart and the token is returned")
    void registerCustomer_emailNotRegistered_savesCustomerAndCartAndReturnsToken() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass123");

        Cliente clienteSinId = new Cliente();
        clienteSinId.setEmail(dto.email());

        Cliente clienteConId = new Cliente();
        clienteConId.setId(99L);
        clienteConId.setEmail(dto.email());

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.DtoLoginACliente(dto)).thenReturn(clienteSinId);
        when(passwordEncoder.encode(dto.password())).thenReturn("encoded-pass");
        when(customerRepository.saveAndFlush(clienteSinId)).thenReturn(clienteConId);
        when(cartRepository.saveAndFlush(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.getToken(clienteConId)).thenReturn("jwt-nuevo-cliente");

        // When
        AuthResponse response = authService.registerCustomer(dto);

        // Then
        assertNotNull(response);
        assertEquals("jwt-nuevo-cliente", response.token());

        verify(passwordEncoder).encode(dto.password());
        verify(customerRepository).saveAndFlush(clienteSinId);

        ArgumentCaptor<Carrito> carritoCaptor = ArgumentCaptor.forClass(Carrito.class);
        verify(cartRepository).saveAndFlush(carritoCaptor.capture());
        Carrito carritoGuardado = carritoCaptor.getValue();
        assertEquals(clienteConId, carritoGuardado.getCliente());
        assertNotNull(carritoGuardado.getFechaCreacion());
    }

    @Test
    @DisplayName("When registering a customer with an email that already exists, it throws EmailYaExisteException and saves nothing")
    void registerCustomer_emailAlreadyExists_throwsEmailAlreadyExists() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("existente@mail.com", "cualquierpass");

        when(customerRepository.existsByEmail(dto.email())).thenReturn(true);

        // When / Then
        assertThrows(EmailYaExisteException.class,
                () -> authService.registerCustomer(dto));

        verify(customerRepository).existsByEmail(dto.email());
        verify(customerMappers, never()).DtoLoginACliente(any());
        verify(customerRepository, never()).save(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("When registering a customer and saving the cart fails, it throws ErrorAlCrearCarritoException and does not generate a token")
    void registerCustomer_cartSaveFails_throwsCartCreationError() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass");

        Cliente clienteSinId = new Cliente();
        clienteSinId.setEmail(dto.email());

        Cliente clienteConId = new Cliente();
        clienteConId.setId(1L);

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.DtoLoginACliente(dto)).thenReturn(clienteSinId);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(customerRepository.saveAndFlush(clienteSinId)).thenReturn(clienteConId);
        when(cartRepository.saveAndFlush(any(Carrito.class)))
                .thenThrow(new RuntimeException("Error de BD"));

        // When / Then
        assertThrows(CarritoExceptions.ErrorAlCrearCarritoException.class,
                () -> authService.registerCustomer(dto));

        // The token must never be generated if the cart failed
        verify(jwtService, never()).getToken(any());
    }


    //  NEW TESTS 

    //  login (new)
    @Test
    @DisplayName("When logging in, the email is normalized (trimmed and lowercase) before authenticating and querying")
    void login_emailWithSpacesAndUppercase_isNormalized() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("  Tilin@Gmail.COM ", "secreto123");

        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setEmail("tilin@gmail.com");

        when(customerRepository.findByEmail("tilin@gmail.com")).thenReturn(Optional.of(cliente));
        when(jwtService.getToken(cliente)).thenReturn("jwt-token-fake");

        // When
        AuthResponse response = authService.login(dto);

        // Then
        assertEquals("jwt-token-fake", response.token());
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("tilin@gmail.com", "secreto123")
        );
        verify(customerRepository).findByEmail("tilin@gmail.com");
    }

    //  registerCustomer (new)
    @Test
    @DisplayName("When registering a customer, the password is stored encoded and never in plain text")
    void registerCustomer_storesEncodedPassword() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass123");
        Cliente cliente = new Cliente();

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.DtoLoginACliente(dto)).thenReturn(cliente);
        when(passwordEncoder.encode("pass123")).thenReturn("HASH");
        when(customerRepository.saveAndFlush(cliente)).thenReturn(cliente);
        when(jwtService.getToken(cliente)).thenReturn("jwt");

        // When
        authService.registerCustomer(dto);

        // Then
        assertEquals("HASH", cliente.getPassword());
        assertNotEquals("pass123", cliente.getPassword());
    }

    @Test
    @DisplayName("When registering a customer, the email is normalized before checking and saving it")
    void registerCustomer_emailWithSpacesAndUppercase_isNormalized() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("  Nuevo@Mail.COM ", "pass123");
        Cliente cliente = new Cliente();

        when(customerRepository.existsByEmail("nuevo@mail.com")).thenReturn(false);
        when(customerMappers.DtoLoginACliente(dto)).thenReturn(cliente);
        when(passwordEncoder.encode("pass123")).thenReturn("HASH");
        when(customerRepository.saveAndFlush(cliente)).thenReturn(cliente);
        when(jwtService.getToken(cliente)).thenReturn("jwt");

        // When
        authService.registerCustomer(dto);

        // Then
        assertEquals("nuevo@mail.com", cliente.getEmail());
        verify(customerRepository).existsByEmail("nuevo@mail.com");
    }

    @Test
    @DisplayName("When registering an email that exists with different casing, it throws EmailYaExisteException")
    void registerCustomer_existingEmailWithDifferentCase_throwsEmailAlreadyExists() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("Existente@MAIL.com", "pass123");
        when(customerRepository.existsByEmail("existente@mail.com")).thenReturn(true);

        // When / Then
        assertThrows(EmailYaExisteException.class, () -> authService.registerCustomer(dto));
        verifyNoInteractions(cartRepository, jwtService);
    }

    @Test
    @DisplayName("When registering and the database rejects the email as a duplicate, it throws EmailYaExisteException without creating a cart or a token")
    void registerCustomer_dataIntegrity_throwsEmailAlreadyExists() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass123");
        Cliente cliente = new Cliente();

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.DtoLoginACliente(dto)).thenReturn(cliente);
        when(passwordEncoder.encode(dto.password())).thenReturn("HASH");
        when(customerRepository.saveAndFlush(cliente))
                .thenThrow(new DataIntegrityViolationException("unique email"));

        // When / Then
        assertThrows(EmailYaExisteException.class, () -> authService.registerCustomer(dto));
        verifyNoInteractions(cartRepository, jwtService);
    }

    @Test
    @DisplayName("When registering a customer, the steps run in order: check email, save customer, create cart, generate token")
    void registerCustomer_executesStepsInOrder() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass123");
        Cliente cliente = new Cliente();
        cliente.setId(5L);

        when(customerRepository.existsByEmail(dto.email())).thenReturn(false);
        when(customerMappers.DtoLoginACliente(dto)).thenReturn(cliente);
        when(passwordEncoder.encode(dto.password())).thenReturn("HASH");
        when(customerRepository.saveAndFlush(cliente)).thenReturn(cliente);
        when(jwtService.getToken(cliente)).thenReturn("jwt");

        // When
        authService.registerCustomer(dto);

        // Then
        InOrder orden = inOrder(customerRepository, cartRepository, jwtService);
        orden.verify(customerRepository).existsByEmail(dto.email());
        orden.verify(customerRepository).saveAndFlush(cliente);
        orden.verify(cartRepository).saveAndFlush(any(Carrito.class));
        orden.verify(jwtService).getToken(cliente);
    }
}