package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ClienteNoEncontradoException;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cliente Service - Pruebas unitarias")
class ClienteServiceTest {
    @InjectMocks
    private ClienteService clienteService;
    @Mock
    private ClienteRepository clienteRepository;

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ClienteMappers clienteMappers;
    private final Faker faker = new Faker();

    private Cliente generarClienteFake() {
        Cliente c = new Cliente();
        c.setId(faker.number().randomNumber());
        c.setEmail(faker.internet().safeEmailAddress());
        c.setPassword("");
        return c;
    }


    @Test
    void obtenerCliente_clienteNoExiste_lanzaClienteNoEncontradoException() {
        // Given
        Cliente cliente = generarClienteFake();

        when(clienteRepository.findById(cliente.getId()))
                .thenReturn(Optional.empty());

        // When + Then
        ClienteNoEncontradoException ex = assertThrows(
                ClienteNoEncontradoException.class,
                () -> clienteService.obtenerCliente(cliente)
        );

        // Then
        //System.out.println(ex.getMessage());
        assertTrue(ex.getMessage().contains(cliente.getEmail()));
        verify(clienteRepository).findById(cliente.getId());
        verifyNoInteractions(clienteMappers);
    }

}