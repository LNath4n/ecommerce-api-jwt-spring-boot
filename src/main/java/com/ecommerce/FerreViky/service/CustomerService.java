package com.ecommerce.FerreViky.service;


import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.UpdateCustomerRequest;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.PublicCustomerResponse;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.CustomerModifiedException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.InvalidPasswordException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.InvalidImageException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.CustomerNotFoundException;
import com.ecommerce.FerreViky.exceptions.customer.CustomerExceptions.EmailAlreadyExistsException;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.repository.CustomerRepository;
import com.ecommerce.FerreViky.service.storageService.contract.StorageService;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ecommerce.FerreViky.mapper.customer.CustomerMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

@AllArgsConstructor
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final CustomerRepository customerRepository;
    private static final Duration PROFILE_PHOTO_URL_VALIDITY = Duration.ofMinutes(15);

    /**
     * Retrieves a customer's public profile details.
     *
     * <p>Finds the record associated with the given customer and maps it to a
     * public DTO, preventing internal model data from being exposed directly.</p>
     *
     * @param customer customer whose profile is being retrieved
     * @return the customer's public profile details
     * @throws CustomerNotFoundException if no record is associated with the given customer
     */
    public PublicCustomerResponse getCustomer(Customer customer) {
        log.debug("Fetching profile of customer {}", customer.getId());
        Customer customerRecord = customerRepository.findById(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Profile fetch failed: customer {} not found", customer.getId());
                    return new CustomerNotFoundException(customer.getEmail());
                });

        return toPublicResponse(customerRecord);
    }

    /**
     * Updates the profile of the customer identified by their ID.
     *
     * <p>Only fields that are not {@code null} in the request are updated.
     * Omitted fields keep their current values.</p>
     *
     * <p>Changing the password requires the current password. The new password
     * is encoded with {@link PasswordEncoder} before it is stored.</p>
     *
     * <p>When the email address is changed, the service checks that another
     * customer is not already using it.</p>
     *
     * @param customer authenticated customer whose profile is being updated
     * @param request profile changes
     * @return the updated customer's public profile details
     * @throws CustomerNotFoundException if no customer exists with the given ID
     * @throws InvalidPasswordException if the current password is missing or does not match
     * @throws EmailAlreadyExistsException if another customer already uses the new email address
     */
    @Transactional
    public PublicCustomerResponse updateCustomer(
            Customer customer,
            UpdateCustomerRequest request) {

        log.debug("Updating profile of customer {}", customer.getId());
        Customer customerRecord = customerRepository.findById(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Update failed: customer {} not found", customer.getId());
                    return new CustomerNotFoundException(customer.getEmail());
                });

        boolean closeSessions = Boolean.TRUE.equals(request.closeSessions());

        // Updates the password only when a change is requested. / Actualiza la contraseña únicamente si se solicita el cambio.
        if (request.newPassword() != null) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(
                    request.currentPassword(), customerRecord.getPassword())) {
                log.warn("Password change rejected for customer {}: current password missing or incorrect",
                        customerRecord.getId());
                throw new InvalidPasswordException();
            }

            customerRecord.setPassword(passwordEncoder.encode(request.newPassword()));
            log.info("Password changed for customer {}", customerRecord.getId());
        }

        // Prevents assigning an email address already used by another customer. / Evita asignar un correo que ya pertenezca a otro cliente.
        String newEmail = normalizeEmail(request.email());

        if (newEmail != null
                && !newEmail.isBlank()
                && !newEmail.equals(customerRecord.getEmail())) {

            if (customerRepository.existsByEmail(newEmail)) {
                log.warn("Email change rejected for customer {}: email is already in use", customerRecord.getId());
                throw new EmailAlreadyExistsException(newEmail);
            }

            customerRecord.setEmail(newEmail);
            log.info("Email changed for customer {}", customerRecord.getId());
        }

        // Updates the personal details included in the request. / Actualiza los datos personales enviados en la solicitud.
        if (request.name() != null && !request.name().isBlank()) {
            customerRecord.setName(request.name());
            log.debug("Name updated for customer {}", customerRecord.getId());
        }

        if (request.phone() != null) {
            customerRecord.setPhone(request.phone());
            log.debug("Phone updated for customer {}", customerRecord.getId());
        }

        // Invalidates previously issued tokens only when requested. / Invalida todos los tokens emitidos antes (solo si se pidió).
        if (closeSessions) {
            customerRecord.setTokenVersion(customerRecord.getTokenVersion() + 1);
            log.info("Sessions closed for customer {} (tokenVersion={})",
                    customerRecord.getId(), customerRecord.getTokenVersion());
        }

        try {
            customerRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected while updating customer {}", customerRecord.getId());
            throw new CustomerModifiedException(customerRecord.getId(), e);
        }

        log.info("Profile updated for customer {}", customerRecord.getId());
        return toPublicResponse(customerRecord);
    }

    /**
     * Deactivates a customer's account without deleting the record.
     *
     * <p>Finds the corresponding record and sets its status to
     * {@code active = false}. The change is persisted when the transaction
     * completes successfully.</p>
     *
     * @param customer customer whose account is being deactivated
     * @throws CustomerNotFoundException if no record is associated with the given customer
     */
    @Transactional
    public void deactivateCustomer(Customer customer) {
        log.debug("Deactivating customer {}", customer.getId());
        Customer customerRecord = customerRepository.findById(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Deactivation failed: customer {} not found", customer.getId());
                    return new CustomerNotFoundException(customer.getEmail());
                });

        customerRecord.setActive(false);
        log.info("Customer {} deactivated", customerRecord.getId());
    }

    /**
     * Activates a customer's account without changing other account data.
     *
     * <p>Finds the corresponding record and sets its status to
     * {@code active = true}. The change is persisted when the transaction
     * completes successfully.</p>
     *
     * @param customer customer whose account is being activated
     * @throws CustomerNotFoundException if no record is associated with the given customer
     */
    @Transactional
    public void activateCustomer(Customer customer) {
        log.debug("Activating customer {}", customer.getId());
        Customer customerRecord = customerRepository.findById(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Activation failed: customer {} not found", customer.getId());
                    return new CustomerNotFoundException(customer.getEmail());
                });

        customerRecord.setActive(true);
        log.info("Customer {} activated", customerRecord.getId());
    }


    /**
     * Uploads or replaces a customer's profile photo.
     *
     * <p>Uploads the new image to the private profile folder and stores its key
     * in the database. The previous photo is deleted from the bucket only after
     * the transaction commits. If the transaction fails, the previous photo is
     * kept and the newly uploaded one is deleted.</p>
     *
     * @param customer authenticated customer who owns the photo
     * @param file image to upload (JPG or PNG only)
     * @return the customer's public profile details with the updated photo
     * @throws CustomerNotFoundException if no record is associated with the given customer
     * @throws InvalidImageException if the file is not a valid image, its format is unsupported, or it exceeds the maximum dimensions
     * @throws IOException if the file cannot be read
     */
    @Transactional
    public PublicCustomerResponse uploadCustomerPhoto(Customer customer, MultipartFile file) throws IOException {
        log.debug("Uploading profile photo for customer {} (size={} bytes, contentType={})",
                customer.getId(), file.getSize(), file.getContentType());
        Customer customerRecord = customerRepository.findById(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Photo upload failed: customer {} not found", customer.getId());
                    return new CustomerNotFoundException(customer.getEmail());
                });

        String previousImageKey = customerRecord.getImageKey();
        customerRecord.setImageKey(storageService.upload(file, StorageService.StorageFolder.PROFILES));

        log.info("Profile photo uploaded for customer {} (replaced a previous photo: {})",
                customerRecord.getId(), previousImageKey != null);
        storageService.deleteAfterCommit(previousImageKey);

        return toPublicResponse(customerRecord);
    }

    /**
     * Removes a customer's profile photo.
     *
     * <p>Sets the image key to {@code null} and deletes the file from the bucket
     * only after the transaction commits. If the customer has no photo, nothing
     * happens.</p>
     *
     * @param customer authenticated customer who owns the photo
     * @return the customer's public profile details without a photo
     * @throws CustomerNotFoundException if no record is associated with the given customer
     */
    @Transactional
    public PublicCustomerResponse removeCustomerPhoto(Customer customer) {
        log.debug("Removing profile photo of customer {}", customer.getId());
        Customer customerRecord = customerRepository.findById(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Photo removal failed: customer {} not found", customer.getId());
                    return new CustomerNotFoundException(customer.getEmail());
                });

        String imageKey = customerRecord.getImageKey();
        customerRecord.setImageKey(null);
        log.info("Profile photo removed for customer {} (had a photo: {})", customerRecord.getId(), imageKey != null);
        storageService.deleteAfterCommit(imageKey);   // Deletes from S3 only after the database transaction commits. / Borra de S3 solo si la BD confirma.

        return toPublicResponse(customerRecord);
    }

    /**
     * Converts a customer to their public representation, including a signed
     * URL for their profile photo.
     *
     * <p>The photo is stored in a private bucket folder, so a signed read URL
     * valid for {@link #PROFILE_PHOTO_URL_VALIDITY} is generated. If the customer has no photo,
     * the DTO's {@code photoUrl} field is {@code null}.</p>
     *
     * <p>The URL expires and should not be stored. Generate a new one each time
     * the profile is retrieved.</p>
     *
     * @param customerRecord customer entity to convert
     * @return the customer's public details with the signed photo URL, or {@code null} if there is no photo
     */
    private PublicCustomerResponse toPublicResponse(Customer customerRecord) {
        String photoUrl = storageService.getPresignedUrl(customerRecord.getImageKey(), PROFILE_PHOTO_URL_VALIDITY);
        return customerMapper.toPublicCustomerResponse(customerRecord, photoUrl);
    }


    /**
     * Normalizes an email address by trimming whitespace and converting it to
     * lowercase. Returns {@code null} if the input is {@code null}.
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}