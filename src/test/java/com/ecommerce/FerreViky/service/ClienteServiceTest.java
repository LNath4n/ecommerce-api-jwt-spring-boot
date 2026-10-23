package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.ClienteEditarRequest;
import com.ecommerce.FerreViky.dto.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.EmailYaExisteException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ContrasenaInvalida;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ClienteNoEncontradoException;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static com.ecommerce.FerreViky.utils.Fakes.*;
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

    @Mock private StorageService storageService;
    @Mock private ClienteMappers clienteMappers;


    @Test
    @DisplayName("Al mandar a llamar al metodo devolverClientePublico este lo devuelve correctamente con informacion de un cliente")
    void obtenerCliente_existe_devuelveClientePublico() {
        // Arrange
        Cliente c = generarClienteFake();
        String fotoUrl = "http://foto.com/abc";
        ClientePublico esperado = new ClientePublico(c.getEmail(),c.getNombre(),c.getTelefono(),fotoUrl);

        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.getPresignedUrl(c.getImagenKey(), Duration.ofMinutes(15))).thenReturn(fotoUrl);
        when(clienteMappers.devolverClientePublico(c,fotoUrl)).thenReturn((esperado));

        // Act
        ClientePublico resultado = clienteService.obtenerCliente(c);

        // Assert
        assertEquals(esperado, resultado);
        verify(clienteRepository).findById(c.getId());
    }

    @Test
    @DisplayName("Al no existir un cliente el metodo obtenerCliente lanza ClienteNoEncontradoException")
    void obtenerCliente_noExiste_lanzaClienteNoEncontrado() {
        //Arrange
        Cliente c = generarClienteFake();
        //Act
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.empty());
        //Assert
        assertThrows(ClienteNoEncontradoException.class,()->clienteService.obtenerCliente(c));
    }

    @Test
    @DisplayName("Al buscar un cliente que no existe para editarlo este lanza una excepcion")
    void editarCliente_noExiste_lanzaClienteNoEncontrado() {
        //Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest clienteEditarRequest = new ClienteEditarRequest(c.getEmail(),c.getPassword(),"NuevaPassword",c.getNombre(),c.getTelefono());
        //Act
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.empty());
        //Assert
        assertThrows(ClienteNoEncontradoException.class,()->clienteService.editarCliente(c,clienteEditarRequest));
    }

    @Test
    @DisplayName("Al tratar de editar un cliente con password nueva sin passwrod vieja lanza una excepcion")
    void editarCliente_passwordNuevaSinPasswordVieja_lanzaContrasenaInvalida() {
        //Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest clienteEditarRequest = new ClienteEditarRequest(c.getEmail(),null,"NuevaPassword",c.getNombre(),c.getTelefono());
        //Act
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));
        //Assert
        assertThrows(ContrasenaInvalida.class,()->clienteService.editarCliente(c,clienteEditarRequest));
    }

    @Test
    @DisplayName("Al tratar de editar un cliente con una password vieja que no coincida con la actual")
    void editarCliente_passwordViejaIncorrecta_lanzaContrasenaInvalida() {
        //Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest clienteEditarRequest = new ClienteEditarRequest(c.getEmail(),"DefinitivamenteNoLaPassword","NuevaPassword",c.getNombre(),c.getTelefono());
        //Act
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));
        //Assert
        assertThrows(ContrasenaInvalida.class,()->clienteService.editarCliente(c,clienteEditarRequest));
    }


    @Test
    @DisplayName("Se actualiza correctamente la contrasena nueva del Cliente")
    void editarCliente_passwordCorrecta_guardaPasswordCodificada() {
        // Arrange
        Cliente c = generarClienteFake();
        String vieja = c.getPassword();
        String contrasenaNueva = "NuevaPassword";
        String hashNuevo = "HASH_NUEVO";

        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), vieja, contrasenaNueva, c.getNombre(), c.getTelefono());

        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(passwordEncoder.matches(vieja, vieja)).thenReturn(true);
        when(passwordEncoder.encode(contrasenaNueva)).thenReturn(hashNuevo);

        // Act
        clienteService.editarCliente(c, request);

        // Assert
        assertEquals(hashNuevo, c.getPassword());
        verify(passwordEncoder).encode(contrasenaNueva);
    }

    @Test
    @DisplayName("Al tratar de cambiar el correo de un cliente a uno ya existente marca error")
    void editarCliente_emailYaExiste_lanzaEmailYaExiste() {
        //Arrange
        Cliente clienteAEditar = generarClienteFake();
        Cliente clienteYaExistente = generarClienteFake(cli -> cli.setEmail("ocupado@mail.com"));

        ClienteEditarRequest request = new ClienteEditarRequest(clienteYaExistente.getEmail(), null,null, clienteAEditar.getNombre(), clienteAEditar.getTelefono());
        when(clienteRepository.findById(clienteAEditar.getId())).thenReturn(Optional.of(clienteAEditar));
        when(clienteRepository.existsByEmail(clienteYaExistente.getEmail())).thenReturn(true);

        //Act + Assert
        assertThrows(EmailYaExisteException.class, ()-> clienteService.editarCliente(clienteAEditar,request));
    }

    @Test
    @DisplayName("Al actualizar el correo de un Cliente este se actualiza de verdad")
    void editarCliente_emailNuevoDisponible_actualizaEmail() {
        //Arrange
        Cliente c = generarClienteFake();
        String correo = "NuevoCorreo@Gmail.com";
        ClienteEditarRequest request = new ClienteEditarRequest(correo, null,null, c.getNombre(), c.getTelefono());

        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        clienteService.editarCliente(c, request);

        //Assert
        assertEquals(correo, c.getEmail());
    }

    @Test
    @DisplayName("Al editar un cliente con el mismo email este no consulta existsByEmail")
    void editarCliente_mismoEmail_noConsultaRepositorio() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono());
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        clienteService.editarCliente(c, request);

        // Assert
        verify(clienteRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("Al recibir una Request sin los campos opcionales conserva los anteriores")
    void editarCliente_nombreYTelefonoNull_conservaValoresActuales() {
        //Arrange
        Cliente c = generarClienteFake();
        String nombreCliente = c.getNombre();
        String telefonoCliente = c.getTelefono();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, null, null);
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));

        //Act
        clienteService.editarCliente(c, request);

        //Assert
        assertEquals(nombreCliente,c.getNombre());
        assertEquals(telefonoCliente,c.getTelefono());
    }

    @Test
    @DisplayName("Al editar el Nombre y el Telefono del Cliente se actualizan")
    void editarCliente_nombreYTelefono_actualizaCampos() {
        //Arrange
        Cliente c = generarClienteFake();
        String nombreCliente = "Vargas";
        String telefonoCliente = "01234567890";
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, nombreCliente, telefonoCliente);
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));

        //Act
        clienteService.editarCliente(c, request);

        //Assert
        assertEquals(nombreCliente, c.getNombre());
        assertEquals(telefonoCliente, c.getTelefono());
    }

    @Test
    @DisplayName("Al desactivar una cuenta esta cambia su estado en False")
    void desactivarCliente_existe_poneActivoEnFalse() {
        //Arrange
        Cliente c = generarClienteFake();
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));
        //Act
        clienteService.desactivarCliente(c);
        //Assert
        assertFalse(c.isActivo());
    }

    @Test
    @DisplayName("Al desactivar una cuenta inexistente esta arroja una excepcion")
    void desactivarCliente_noExiste_lanzaClienteNoEncontrado() {
        //Arrange
        Cliente c = generarClienteFake();
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.empty());

        //Act + Assert
        assertThrows(ClienteNoEncontradoException.class,()->clienteService.desactivarCliente(c));
    }

    @Test
    @DisplayName("Al activar una cuenta esta cambia su estado a true")
    void activarCliente_existe_poneActivoEnTrue() {
        // Arrange
        Cliente c = generarClienteFake(cli -> cli.setActivo(false));
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        clienteService.activarCliente(c);

        // Assert
        assertTrue(c.isActivo());
    }

    @Test
    @DisplayName("Al activar una cuenta inexistente esta arroja una excepcion")
    void activarCliente_noExiste_lanzaClienteNoEncontrado() {
        // Arrange
        Cliente c = generarClienteFake();
        when(clienteRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                ClienteNoEncontradoException.class,
                () -> clienteService.activarCliente(c)
        );
    }


}