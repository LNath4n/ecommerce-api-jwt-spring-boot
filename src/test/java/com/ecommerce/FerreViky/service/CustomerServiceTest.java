package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClienteEditarRequest;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ClienteModificadoException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.EmailYaExisteException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ContrasenaInvalida;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ClienteNoEncontradoException;
import com.ecommerce.FerreViky.exceptions.cliente.ClienteExceptions.ImagenInvalidaException;
import com.ecommerce.FerreViky.mapper.cliente.ClienteMappers;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.repository.ClienteRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static com.ecommerce.FerreViky.utils.Fakes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService - Unit tests")
class CustomerServiceTest {
    @InjectMocks
    private CustomerService customerService;
    @Mock
    private ClienteRepository customerRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private StorageService storageService;
    @Mock private ClienteMappers customerMappers;


    //  getCustomer
    @Test
    @DisplayName("When getting an existing customer, it returns their public profile with the signed photo URL")
    void getCustomer_exists_returnsPublicCustomer() {
        // Arrange
        Cliente c = generarClienteFake();
        String fotoUrl = "http://foto.com/abc";
        ClientePublico esperado = new ClientePublico(c.getEmail(),c.getNombre(),c.getTelefono(),fotoUrl);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.getPresignedUrl(c.getImagenKey(), Duration.ofMinutes(15))).thenReturn(fotoUrl);
        when(customerMappers.devolverClientePublico(c,fotoUrl)).thenReturn((esperado));

        // Act
        ClientePublico resultado = customerService.getCustomer(c);

        // Assert
        assertEquals(esperado, resultado);
        verify(customerRepository).findById(c.getId());
    }

    @Test
    @DisplayName("When the customer does not exist, getCustomer throws ClienteNoEncontradoException")
    void getCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());
        // Assert
        assertThrows(ClienteNoEncontradoException.class,()-> customerService.getCustomer(c));
    }

    //  updateCustomer
    @Test
    @DisplayName("When updating a customer that does not exist, it throws ClienteNoEncontradoException")
    void updateCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest clienteEditarRequest = new ClienteEditarRequest(c.getEmail(),c.getPassword(),"NuevaPassword",c.getNombre(),c.getTelefono(),false);
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());
        // Assert
        assertThrows(ClienteNoEncontradoException.class,()-> customerService.updateCustomer(c,clienteEditarRequest));
    }

    @Test
    @DisplayName("When updating a customer with a new password but no old password, it throws ContrasenaInvalida")
    void updateCustomer_newPasswordWithoutOldPassword_throwsInvalidPassword() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest clienteEditarRequest = new ClienteEditarRequest(c.getEmail(),null,"NuevaPassword",c.getNombre(),c.getTelefono(),false);
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        // Assert
        assertThrows(ContrasenaInvalida.class,()-> customerService.updateCustomer(c,clienteEditarRequest));
    }

    @Test
    @DisplayName("When updating a customer with an old password that does not match the current one, it throws ContrasenaInvalida")
    void updateCustomer_wrongOldPassword_throwsInvalidPassword() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest clienteEditarRequest = new ClienteEditarRequest(c.getEmail(),"DefinitivamenteNoLaPassword","NuevaPassword",c.getNombre(),c.getTelefono(),false);
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        // Assert
        assertThrows(ContrasenaInvalida.class,()-> customerService.updateCustomer(c,clienteEditarRequest));
    }


    @Test
    @DisplayName("When the old password is correct, the new password is stored encoded")
    void updateCustomer_correctPassword_savesEncodedPassword() {
        // Arrange
        Cliente c = generarClienteFake();
        String vieja = c.getPassword();
        String contrasenaNueva = "NuevaPassword";
        String hashNuevo = "HASH_NUEVO";

        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), vieja, contrasenaNueva, c.getNombre(), c.getTelefono(),false);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(passwordEncoder.matches(vieja, vieja)).thenReturn(true);
        when(passwordEncoder.encode(contrasenaNueva)).thenReturn(hashNuevo);

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(hashNuevo, c.getPassword());
        verify(passwordEncoder).encode(contrasenaNueva);
    }

    @Test
    @DisplayName("When changing a customer's email to one that already exists, it throws EmailYaExisteException")
    void updateCustomer_emailAlreadyExists_throwsEmailAlreadyExists() {
        // Arrange
        Cliente clienteAEditar = generarClienteFake();
        Cliente clienteYaExistente = generarClienteFake(cli -> cli.setEmail("ocupado@mail.com"));

        ClienteEditarRequest request = new ClienteEditarRequest(clienteYaExistente.getEmail(), null,null, clienteAEditar.getNombre(), clienteAEditar.getTelefono(),false);
        when(customerRepository.findById(clienteAEditar.getId())).thenReturn(Optional.of(clienteAEditar));
        when(customerRepository.existsByEmail(clienteYaExistente.getEmail())).thenReturn(true);

        // Act + Assert
        assertThrows(EmailYaExisteException.class, ()-> customerService.updateCustomer(clienteAEditar,request));
    }

    @Test
    @DisplayName("When updating a customer's email, it is actually changed")
    void updateCustomer_newEmailAvailable_updatesEmail() {
        // Arrange
        Cliente c = generarClienteFake();
        String correo = "NuevoCorreo@Gmail.com".toLowerCase(Locale.ROOT);
        ClienteEditarRequest request = new ClienteEditarRequest(correo, null,null, c.getNombre(), c.getTelefono(),false);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(correo, c.getEmail());
    }

    @Test
    @DisplayName("When updating a customer with the same email, existsByEmail is not queried")
    void updateCustomer_sameEmail_doesNotQueryRepository() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono(),false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        verify(customerRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("When the request has no optional fields, the previous values are kept")
    void updateCustomer_nullNameAndPhone_keepsCurrentValues() {
        // Arrange
        Cliente c = generarClienteFake();
        String nombreCliente = c.getNombre();
        String telefonoCliente = c.getTelefono();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, null, null,false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(nombreCliente,c.getNombre());
        assertEquals(telefonoCliente,c.getTelefono());
    }

    @Test
    @DisplayName("When editing the customer's name and phone, they are updated")
    void updateCustomer_nameAndPhone_updatesFields() {
        // Arrange
        Cliente c = generarClienteFake();
        String nombreCliente = "Vargas";
        String telefonoCliente = "01234567890";
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, nombreCliente, telefonoCliente,false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(nombreCliente, c.getNombre());
        assertEquals(telefonoCliente, c.getTelefono());
    }

    //  deactivateCustomer / activateCustomer
    @Test
    @DisplayName("When deactivating an account, its status changes to false")
    void deactivateCustomer_exists_setsActiveToFalse() {
        // Arrange
        Cliente c = generarClienteFake();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        // Act
        customerService.deactivateCustomer(c);
        // Assert
        assertFalse(c.isActivo());
    }

    @Test
    @DisplayName("When deactivating a nonexistent account, it throws ClienteNoEncontradoException")
    void deactivateCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ClienteNoEncontradoException.class,()-> customerService.deactivateCustomer(c));
    }

    @Test
    @DisplayName("When activating an account, its status changes to true")
    void activateCustomer_exists_setsActiveToTrue() {
        // Arrange
        Cliente c = generarClienteFake(cli -> cli.setActivo(false));
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.activateCustomer(c);

        // Assert
        assertTrue(c.isActivo());
    }

    @Test
    @DisplayName("When activating a nonexistent account, it throws ClienteNoEncontradoException")
    void activateCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                ClienteNoEncontradoException.class,
                () -> customerService.activateCustomer(c)
        );
    }


    //  NEW TESTS

    //  updateCustomer (new)
    @Test
    @DisplayName("When updating a customer with a wrong old password, the new password is never encoded or saved")
    void updateCustomer_wrongOldPassword_doesNotEncodeOrChangePassword() {
        // Arrange
        Cliente c = generarClienteFake();
        String passwordOriginal = c.getPassword();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), "DefinitivamenteNoLaPassword", "NuevaPassword", c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act + Assert
        assertThrows(ContrasenaInvalida.class, () -> customerService.updateCustomer(c, request));
        verify(passwordEncoder, never()).encode(any());
        assertEquals(passwordOriginal, c.getPassword());
    }

    @Test
    @DisplayName("When updating a customer without a new password, the password is left untouched")
    void updateCustomer_withoutNewPassword_doesNotTouchPassword() {
        // Arrange
        Cliente c = generarClienteFake();
        String passwordOriginal = c.getPassword();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(passwordOriginal, c.getPassword());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("When updating a customer's email, it is normalized (trimmed and lowercase) before being checked and saved")
    void updateCustomer_emailWithSpacesAndUppercase_isNormalized() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest request = new ClienteEditarRequest(
                "  Nuevo@Gmail.COM ", null, null, c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals("nuevo@gmail.com", c.getEmail());
        verify(customerRepository).existsByEmail("nuevo@gmail.com");
    }

    @Test
    @DisplayName("When the request email is blank, the current email is kept and the repository is not queried")
    void updateCustomer_blankEmail_keepsCurrentEmail() {
        // Arrange
        Cliente c = generarClienteFake();
        String emailOriginal = c.getEmail();
        ClienteEditarRequest request = new ClienteEditarRequest(
                "   ", null, null, c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(emailOriginal, c.getEmail());
        verify(customerRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("When the request name is blank, the current name is kept")
    void updateCustomer_blankName_keepsCurrentName() {
        // Arrange
        Cliente c = generarClienteFake();
        String nombreOriginal = c.getNombre();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, "   ", c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(nombreOriginal, c.getNombre());
    }

    @Test
    @DisplayName("When closing sessions is requested, the customer's token version is incremented")
    void updateCustomer_closeSessions_incrementsTokenVersion() {
        // Arrange
        Cliente c = generarClienteFake();
        var versionAntes = c.getTokenVersion();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono(), true);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertNotEquals(versionAntes, c.getTokenVersion());
        assertTrue(c.getTokenVersion() > versionAntes);
    }

    @Test
    @DisplayName("When closing sessions is not requested, the customer's token version is unchanged")
    void updateCustomer_withoutCloseSessions_keepsTokenVersion() {
        // Arrange
        Cliente c = generarClienteFake();
        var versionAntes = c.getTokenVersion();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(versionAntes, c.getTokenVersion());
    }

    @Test
    @DisplayName("When updating a customer successfully, flush is called")
    void updateCustomer_success_flushesRepository() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        verify(customerRepository).flush();
    }

    @Test
    @DisplayName("When updating a customer modified concurrently by another user, it throws ClienteModificadoException")
    void updateCustomer_optimisticLock_throwsCustomerModified() {
        // Arrange
        Cliente c = generarClienteFake();
        ClienteEditarRequest request = new ClienteEditarRequest(
                c.getEmail(), null, null, c.getNombre(), c.getTelefono(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        doThrow(new ObjectOptimisticLockingFailureException(Cliente.class, c.getId()))
                .when(customerRepository).flush();

        // Act + Assert
        assertThrows(ClienteModificadoException.class,
                () -> customerService.updateCustomer(c, request));
    }

    //  uploadCustomerPhoto
    @Test
    @DisplayName("When uploading a photo, the new key is saved, the previous one's deletion is scheduled and the updated profile is returned")
    void uploadCustomerPhoto_exists_replacesKeyAndSchedulesPreviousDeletion() throws IOException {
        // Arrange
        Cliente c = generarClienteFake(cli -> cli.setImagenKey("perfiles/vieja.png"));
        MultipartFile archivo = mock(MultipartFile.class);
        String fotoUrl = "http://foto.com/nueva";
        ClientePublico esperado = new ClientePublico(c.getEmail(), c.getNombre(), c.getTelefono(), fotoUrl);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.upload(archivo, StorageService.StorageFolder.PROFILES)).thenReturn("perfiles/nueva.png");
        when(storageService.getPresignedUrl("perfiles/nueva.png", Duration.ofMinutes(15))).thenReturn(fotoUrl);
        when(customerMappers.devolverClientePublico(c, fotoUrl)).thenReturn(esperado);

        // Act
        ClientePublico resultado = customerService.uploadCustomerPhoto(c, archivo);

        // Assert
        assertEquals("perfiles/nueva.png", c.getImagenKey());
        assertEquals(esperado, resultado);
        verify(storageService).deleteAfterCommit("perfiles/vieja.png");
    }

    @Test
    @DisplayName("When uploading a photo for a customer that does not exist, it throws ClienteNoEncontradoException without touching storage")
    void uploadCustomerPhoto_notFound_throwsCustomerNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        MultipartFile archivo = mock(MultipartFile.class);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ClienteNoEncontradoException.class,
                () -> customerService.uploadCustomerPhoto(c, archivo));
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("When the uploaded image is invalid, it throws ImagenInvalidaException and keeps the previous photo")
    void uploadCustomerPhoto_invalidImage_throwsInvalidImageAndKeepsPreviousPhoto() throws IOException {
        // Arrange
        Cliente c = generarClienteFake(cli -> cli.setImagenKey("perfiles/vieja.png"));
        MultipartFile archivo = mock(MultipartFile.class);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.upload(archivo, StorageService.StorageFolder.PROFILES))
                .thenThrow(ImagenInvalidaException.class);

        // Act + Assert
        assertThrows(ImagenInvalidaException.class,
                () -> customerService.uploadCustomerPhoto(c, archivo));
        assertEquals("perfiles/vieja.png", c.getImagenKey());
        verify(storageService, never()).deleteAfterCommit(any());
    }

    //  removeCustomerPhoto
    @Test
    @DisplayName("When removing the photo, the key is set to null, file deletion is scheduled and the profile is returned without a photo")
    void removeCustomerPhoto_exists_setsKeyToNullAndSchedulesDeletion() {
        // Arrange
        Cliente c = generarClienteFake(cli -> cli.setImagenKey("perfiles/vieja.png"));
        ClientePublico esperado = new ClientePublico(c.getEmail(), c.getNombre(), c.getTelefono(), null);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(customerMappers.devolverClientePublico(c, null)).thenReturn(esperado);

        // Act
        ClientePublico resultado = customerService.removeCustomerPhoto(c);

        // Assert
        assertNull(c.getImagenKey());
        assertEquals(esperado, resultado);
        verify(storageService).deleteAfterCommit("perfiles/vieja.png");
    }

    @Test
    @DisplayName("When removing the photo of a customer that does not exist, it throws ClienteNoEncontradoException without touching storage")
    void removeCustomerPhoto_notFound_throwsCustomerNotFound() {
        // Arrange
        Cliente c = generarClienteFake();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ClienteNoEncontradoException.class,
                () -> customerService.removeCustomerPhoto(c));
        verifyNoInteractions(storageService);
    }


}