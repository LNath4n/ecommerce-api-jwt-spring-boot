package com.ecommerce.FerreViky.mapper.customer;

import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.PublicCustomerResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerCreatedResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerLoginRequest;
import com.ecommerce.FerreViky.models.Customer;
import org.springframework.stereotype.Component;

/**
 * Maps between customer DTOs and the {@link Customer} entity.
 */
@Component
public class CustomerMapper {

    /**
     * Converts a login or registration DTO to a {@link Customer} entity.
     *
     * <p>Only the email and password are mapped so customers cannot set fields
     * they should not control, such as the ID or role.
     */
    public Customer fromLoginRequest(CustomerLoginRequest loginRequest) {
        Customer customer = new Customer();
        customer.setEmail(loginRequest.email());
        customer.setPassword(loginRequest.password());
        customer.setRole(Customer.Role.USER);
        return customer;
    }

    /**
     * Converts a {@link Customer} entity to the post-registration response,
     * exposing only its assigned ID and email.
     *
     * @param customer persisted entity with a generated ID
     * @return response DTO with the minimum details needed for confirmation
     */
    public CustomerCreatedResponse toCustomerCreatedResponse(Customer customer) {
        return new CustomerCreatedResponse(
                customer.getId(), customer.getEmail()
        );
    }

    public PublicCustomerResponse toPublicCustomerResponse(Customer customer, String photoUrl) {
        return new PublicCustomerResponse(
                customer.getEmail(),
                customer.getName(),
                customer.getPhone(),
                photoUrl
        );
    }
}