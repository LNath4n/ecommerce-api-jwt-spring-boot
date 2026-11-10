package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.UpdateCustomerRequest;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.PublicCustomerResponse;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.CustomerModifiedException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.EmailAlreadyExistsException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.InvalidPasswordException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.CustomerNotFoundException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.InvalidImageException;
import com.ecommerce.FerreViky.mapper.customer.CustomerMapper;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.repository.CustomerRepository;
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

import static com.ecommerce.FerreViky.utils.CustomerFakes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService - Unit tests")
class CustomerServiceTest {
    @InjectMocks
    private CustomerService customerService;
    @Mock
    private CustomerRepository customerRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private StorageService storageService;
    @Mock private CustomerMapper customerMappers;


    //  getCustomer
    @Test
    @DisplayName("When getting an existing customer, it returns their public profile with the signed photo URL")
    void getCustomer_exists_returnsPublicCustomer() {
        // Arrange
        Customer c = fakeCustomer();
        String photoUrl = "http://foto.com/abc";
        PublicCustomerResponse esperado = new PublicCustomerResponse(c.getEmail(),c.getName(),c.getPhone(),photoUrl);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.getPresignedUrl(c.getImageKey(), Duration.ofMinutes(15))).thenReturn(photoUrl);
        when(customerMappers.toPublicCustomerResponse(c,photoUrl)).thenReturn((esperado));

        // Act
        PublicCustomerResponse resultado = customerService.getCustomer(c);

        // Assert
        assertEquals(esperado, resultado);
        verify(customerRepository).findById(c.getId());
    }

    @Test
    @DisplayName("When the customer does not exist, getCustomer throws CustomerNotFoundException")
    void getCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());
        // Assert
        assertThrows(CustomerNotFoundException.class,()-> customerService.getCustomer(c));
    }

    //  updateCustomer
    @Test
    @DisplayName("When updating a customer that does not exist, it throws CustomerNotFoundException")
    void updateCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        UpdateCustomerRequest clienteEditarRequest = new UpdateCustomerRequest(c.getEmail(),c.getPassword(),"NuevaPassword",c.getName(),c.getPhone(),false);
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());
        // Assert
        assertThrows(CustomerNotFoundException.class,()-> customerService.updateCustomer(c,clienteEditarRequest));
    }

    @Test
    @DisplayName("When updating a customer with a new password but no old password, it throws InvalidPasswordException")
    void updateCustomer_newPasswordWithoutOldPassword_throwsInvalidPassword() {
        // Arrange
        Customer c = fakeCustomer();
        UpdateCustomerRequest clienteEditarRequest = new UpdateCustomerRequest(c.getEmail(),null,"NuevaPassword",c.getName(),c.getPhone(),false);
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        // Assert
        assertThrows(InvalidPasswordException.class,()-> customerService.updateCustomer(c,clienteEditarRequest));
    }

    @Test
    @DisplayName("When updating a customer with an old password that does not match the current one, it throws InvalidPasswordException")
    void updateCustomer_wrongOldPassword_throwsInvalidPassword() {
        // Arrange
        Customer c = fakeCustomer();
        UpdateCustomerRequest clienteEditarRequest = new UpdateCustomerRequest(c.getEmail(),"DefinitivamenteNoLaPassword","NuevaPassword",c.getName(),c.getPhone(),false);
        // Act
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        // Assert
        assertThrows(InvalidPasswordException.class,()-> customerService.updateCustomer(c,clienteEditarRequest));
    }


    @Test
    @DisplayName("When the old password is correct, the new password is stored encoded")
    void updateCustomer_correctPassword_savesEncodedPassword() {
        // Arrange
        Customer c = fakeCustomer();
        String vieja = c.getPassword();
        String contrasenaNueva = "NuevaPassword";
        String hashNuevo = "HASH_NUEVO";

        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), vieja, contrasenaNueva, c.getName(), c.getPhone(),false);

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
    @DisplayName("When changing a customer's email to one that already exists, it throws EmailAlreadyExistsException")
    void updateCustomer_emailAlreadyExists_throwsEmailAlreadyExists() {
        // Arrange
        Customer customerAEditar = fakeCustomer();
        Customer customerYaExistente = fakeCustomer(cli -> cli.setEmail("ocupado@mail.com"));

        UpdateCustomerRequest request = new UpdateCustomerRequest(customerYaExistente.getEmail(), null,null, customerAEditar.getName(), customerAEditar.getPhone(),false);
        when(customerRepository.findById(customerAEditar.getId())).thenReturn(Optional.of(customerAEditar));
        when(customerRepository.existsByEmail(customerYaExistente.getEmail())).thenReturn(true);

        // Act + Assert
        assertThrows(EmailAlreadyExistsException.class, ()-> customerService.updateCustomer(customerAEditar,request));
    }

    @Test
    @DisplayName("When updating a customer's email, it is actually changed")
    void updateCustomer_newEmailAvailable_updatesEmail() {
        // Arrange
        Customer c = fakeCustomer();
        String correo = "NuevoCorreo@Gmail.com".toLowerCase(Locale.ROOT);
        UpdateCustomerRequest request = new UpdateCustomerRequest(correo, null,null, c.getName(), c.getPhone(),false);

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
        Customer c = fakeCustomer();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, c.getName(), c.getPhone(),false);
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
        Customer c = fakeCustomer();
        String customerName = c.getName();
        String customerPhone = c.getPhone();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, null, null,false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(customerName,c.getName());
        assertEquals(customerPhone,c.getPhone());
    }

    @Test
    @DisplayName("When editing the customer's name and phone, they are updated")
    void updateCustomer_nameAndPhone_updatesFields() {
        // Arrange
        Customer c = fakeCustomer();
        String customerName = "Vargas";
        String customerPhone = "01234567890";
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, customerName, customerPhone,false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(customerName, c.getName());
        assertEquals(customerPhone, c.getPhone());
    }

    //  deactivateCustomer / activateCustomer
    @Test
    @DisplayName("When deactivating an account, its status changes to false")
    void deactivateCustomer_exists_setsActiveToFalse() {
        // Arrange
        Customer c = fakeCustomer();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        // Act
        customerService.deactivateCustomer(c);
        // Assert
        assertFalse(c.isActive());
    }

    @Test
    @DisplayName("When deactivating a nonexistent account, it throws CustomerNotFoundException")
    void deactivateCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CustomerNotFoundException.class,()-> customerService.deactivateCustomer(c));
    }

    @Test
    @DisplayName("When activating an account, its status changes to true")
    void activateCustomer_exists_setsActiveToTrue() {
        // Arrange
        Customer c = fakeCustomer(cli -> cli.setActive(false));
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.activateCustomer(c);

        // Assert
        assertTrue(c.isActive());
    }

    @Test
    @DisplayName("When activating a nonexistent account, it throws CustomerNotFoundException")
    void activateCustomer_notFound_throwsCustomerNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                CustomerNotFoundException.class,
                () -> customerService.activateCustomer(c)
        );
    }


    //  NEW TESTS

    //  updateCustomer (new)
    @Test
    @DisplayName("When updating a customer with a wrong old password, the new password is never encoded or saved")
    void updateCustomer_wrongOldPassword_doesNotEncodeOrChangePassword() {
        // Arrange
        Customer c = fakeCustomer();
        String passwordOriginal = c.getPassword();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), "DefinitivamenteNoLaPassword", "NuevaPassword", c.getName(), c.getPhone(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act + Assert
        assertThrows(InvalidPasswordException.class, () -> customerService.updateCustomer(c, request));
        verify(passwordEncoder, never()).encode(any());
        assertEquals(passwordOriginal, c.getPassword());
    }

    @Test
    @DisplayName("When updating a customer without a new password, the password is left untouched")
    void updateCustomer_withoutNewPassword_doesNotTouchPassword() {
        // Arrange
        Customer c = fakeCustomer();
        String passwordOriginal = c.getPassword();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, c.getName(), c.getPhone(), false);
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
        Customer c = fakeCustomer();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "  Nuevo@Gmail.COM ", null, null, c.getName(), c.getPhone(), false);
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
        Customer c = fakeCustomer();
        String emailOriginal = c.getEmail();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "   ", null, null, c.getName(), c.getPhone(), false);
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
        Customer c = fakeCustomer();
        String originalName = c.getName();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, "   ", c.getPhone(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        assertEquals(originalName, c.getName());
    }

    @Test
    @DisplayName("When closing sessions is requested, the customer's token version is incremented")
    void updateCustomer_closeSessions_incrementsTokenVersion() {
        // Arrange
        Customer c = fakeCustomer();
        var versionAntes = c.getTokenVersion();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, c.getName(), c.getPhone(), true);
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
        Customer c = fakeCustomer();
        var versionAntes = c.getTokenVersion();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, c.getName(), c.getPhone(), false);
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
        Customer c = fakeCustomer();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, c.getName(), c.getPhone(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        // Act
        customerService.updateCustomer(c, request);

        // Assert
        verify(customerRepository).flush();
    }

    @Test
    @DisplayName("When updating a customer modified concurrently by another user, it throws CustomerModifiedException")
    void updateCustomer_optimisticLock_throwsCustomerModified() {
        // Arrange
        Customer c = fakeCustomer();
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                c.getEmail(), null, null, c.getName(), c.getPhone(), false);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        doThrow(new ObjectOptimisticLockingFailureException(Customer.class, c.getId()))
                .when(customerRepository).flush();

        // Act + Assert
        assertThrows(CustomerModifiedException.class,
                () -> customerService.updateCustomer(c, request));
    }

    //  uploadCustomerPhoto
    @Test
    @DisplayName("When uploading a photo, the new key is saved, the previous one's deletion is scheduled and the updated profile is returned")
    void uploadCustomerPhoto_exists_replacesKeyAndSchedulesPreviousDeletion() throws IOException {
        // Arrange
        Customer c = fakeCustomer(cli -> cli.setImageKey("perfiles/vieja.png"));
        MultipartFile archivo = mock(MultipartFile.class);
        String photoUrl = "http://foto.com/nueva";
        PublicCustomerResponse esperado = new PublicCustomerResponse(c.getEmail(), c.getName(), c.getPhone(), photoUrl);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.upload(archivo, StorageService.StorageFolder.PROFILES)).thenReturn("perfiles/nueva.png");
        when(storageService.getPresignedUrl("perfiles/nueva.png", Duration.ofMinutes(15))).thenReturn(photoUrl);
        when(customerMappers.toPublicCustomerResponse(c, photoUrl)).thenReturn(esperado);

        // Act
        PublicCustomerResponse resultado = customerService.uploadCustomerPhoto(c, archivo);

        // Assert
        assertEquals("perfiles/nueva.png", c.getImageKey());
        assertEquals(esperado, resultado);
        verify(storageService).deleteAfterCommit("perfiles/vieja.png");
    }

    @Test
    @DisplayName("When uploading a photo for a customer that does not exist, it throws CustomerNotFoundException without touching storage")
    void uploadCustomerPhoto_notFound_throwsCustomerNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        MultipartFile archivo = mock(MultipartFile.class);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CustomerNotFoundException.class,
                () -> customerService.uploadCustomerPhoto(c, archivo));
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("When the uploaded image is invalid, it throws InvalidImageException and keeps the previous photo")
    void uploadCustomerPhoto_invalidImage_throwsInvalidImageAndKeepsPreviousPhoto() throws IOException {
        // Arrange
        Customer c = fakeCustomer(cli -> cli.setImageKey("perfiles/vieja.png"));
        MultipartFile archivo = mock(MultipartFile.class);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(storageService.upload(archivo, StorageService.StorageFolder.PROFILES))
                .thenThrow(InvalidImageException.class);

        // Act + Assert
        assertThrows(InvalidImageException.class,
                () -> customerService.uploadCustomerPhoto(c, archivo));
        assertEquals("perfiles/vieja.png", c.getImageKey());
        verify(storageService, never()).deleteAfterCommit(any());
    }

    //  removeCustomerPhoto
    @Test
    @DisplayName("When removing the photo, the key is set to null, file deletion is scheduled and the profile is returned without a photo")
    void removeCustomerPhoto_exists_setsKeyToNullAndSchedulesDeletion() {
        // Arrange
        Customer c = fakeCustomer(cli -> cli.setImageKey("perfiles/vieja.png"));
        PublicCustomerResponse esperado = new PublicCustomerResponse(c.getEmail(), c.getName(), c.getPhone(), null);

        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(customerMappers.toPublicCustomerResponse(c, null)).thenReturn(esperado);

        // Act
        PublicCustomerResponse resultado = customerService.removeCustomerPhoto(c);

        // Assert
        assertNull(c.getImageKey());
        assertEquals(esperado, resultado);
        verify(storageService).deleteAfterCommit("perfiles/vieja.png");
    }

    @Test
    @DisplayName("When removing the photo of a customer that does not exist, it throws CustomerNotFoundException without touching storage")
    void removeCustomerPhoto_notFound_throwsCustomerNotFound() {
        // Arrange
        Customer c = fakeCustomer();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(CustomerNotFoundException.class,
                () -> customerService.removeCustomerPhoto(c));
        verifyNoInteractions(storageService);
    }


}