package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.jwt.JwtService;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.LoginClienteDto;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Pruebas unitarias")
class AuthServiceTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private ClienteMappers clienteMappers;
    @Mock private CarritoRepository carritoRepository;

    @InjectMocks
    private AuthService authService;

    // login()

    @Test
    @DisplayName("Al hacer login con credenciales validas este devuelve un AuthResponse con el token")
    void login_deberiaRetornarToken_cuandoCredencialesValidas() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("tilin@gmail.com", "secreto123");

        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setEmail(dto.email());

        when(clienteRepository.findByEmail(dto.email())).thenReturn(Optional.of(cliente));
        when(jwtService.getToken(cliente)).thenReturn("jwt-token-fake");

        // When
        AuthResponse response = authService.login(dto);

        // Then
        assertNotNull(response);
        assertEquals("jwt-token-fake", response.token());

        // El AuthenticationManager debe haber sido invocado con las credenciales correctas
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
        );
        verify(clienteRepository).findByEmail(dto.email());
        verify(jwtService).getToken(cliente);
    }

    @Test
    @DisplayName("Al hacer login con credenciales invalidas este lanza BadCredentialsException y no consulta el repositorio")
    void login_deberiaLanzarExcepcion_cuandoCredencialesInvalidas() {
        // Given
        // El AuthenticationManager es quien valida: si las creds son malas, lanza BadCredentialsException
        LoginClienteDto dto = new LoginClienteDto("tilin@gmail.com", "passwordMal");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        // When / Then
        assertThrows(BadCredentialsException.class, () -> authService.login(dto));

        // No debe llegar a consultar el repo si la autenticación falla
        verify(clienteRepository, never()).findByEmail(any());
        verify(jwtService, never()).getToken(any());
    }

    // guardarCliente()

    @Test
    @DisplayName("Al guardar un cliente con email nuevo este se guarda junto con su carrito y devuelve el token")
    void guardarCliente_deberiaGuardarClienteYCarritoYRetornarToken_cuandoEmailNoExiste() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass123");

        Cliente clienteSinId = new Cliente();
        clienteSinId.setEmail(dto.email());

        Cliente clienteConId = new Cliente();
        clienteConId.setId(99L);
        clienteConId.setEmail(dto.email());

        when(clienteRepository.existsByEmail(dto.email())).thenReturn(false);
        when(clienteMappers.DtoLoginACliente(dto)).thenReturn(clienteSinId);
        when(passwordEncoder.encode(dto.password())).thenReturn("encoded-pass");
        when(clienteRepository.save(clienteSinId)).thenReturn(clienteConId);
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.getToken(clienteConId)).thenReturn("jwt-nuevo-cliente");

        // When
        AuthResponse response = authService.guardarCliente(dto);

        // Then
        assertNotNull(response);
        assertEquals("jwt-nuevo-cliente", response.token());

        // El password debe haber sido encodeado antes de guardar
        verify(passwordEncoder).encode(dto.password());
        verify(clienteRepository).save(clienteSinId);

        // Verificamos que el carrito guardado esté vinculado al cliente con ID
        ArgumentCaptor<Carrito> carritoCaptor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepository).save(carritoCaptor.capture());
        Carrito carritoGuardado = carritoCaptor.getValue();
        assertEquals(clienteConId, carritoGuardado.getCliente());
        assertNotNull(carritoGuardado.getFechaCreacion());
    }

    @Test
    @DisplayName("Al guardar un cliente con un email que ya existe este lanza EmailYaExisteException y no guarda nada")
    void guardarCliente_deberiaLanzarExcepcion_cuandoEmailYaExiste() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("existente@mail.com", "cualquierpass");

        when(clienteRepository.existsByEmail(dto.email())).thenReturn(true);

        // When / Then
        assertThrows(EmailYaExisteException.class,
                () -> authService.guardarCliente(dto));

        verify(clienteRepository).existsByEmail(dto.email());
        verify(clienteMappers, never()).DtoLoginACliente(any());
        verify(clienteRepository, never()).save(any());
        verify(carritoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Al guardar un cliente y fallar el guardado del carrito este lanza ErrorAlCrearCarritoException y no genera token")    void guardarCliente_deberiaLanzarExcepcion_cuandoFallaGuardarCarrito() {
        // Given
        LoginClienteDto dto = new LoginClienteDto("nuevo@mail.com", "pass");

        Cliente clienteSinId = new Cliente();
        clienteSinId.setEmail(dto.email());

        Cliente clienteConId = new Cliente();
        clienteConId.setId(1L);

        when(clienteRepository.existsByEmail(dto.email())).thenReturn(false);
        when(clienteMappers.DtoLoginACliente(dto)).thenReturn(clienteSinId);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(clienteRepository.save(clienteSinId)).thenReturn(clienteConId);
        when(carritoRepository.save(any(Carrito.class)))
                .thenThrow(new RuntimeException("Error de BD"));

        // When / Then
        assertThrows(CarritoExceptions.ErrorAlCrearCarritoException.class,
                () -> authService.guardarCliente(dto));

        // El token nunca debería generarse si el carrito falló
        verify(jwtService, never()).getToken(any());
    }

    @Test
    @DisplayName("Al hacer login con credenciales validas pero sin cliente en el repositorio este lanza CredencialesInvalidasException y no genera token")
    void login_clienteNoEncontrado_lanzaCredencialesInvalidas() {
        // Arrange
        LoginClienteDto dto = new LoginClienteDto("tilin@gmail.com", "secreto123");
        when(clienteRepository.findByEmail(dto.email())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CredencialesInvalidasException.class, () -> authService.login(dto));
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(jwtService);
    }
}