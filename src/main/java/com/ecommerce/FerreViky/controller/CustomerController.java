package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClienteEditarRequest;
import com.ecommerce.FerreViky.dto.dtos.cliente.ClienteDTO.ClientePublico;
import com.ecommerce.FerreViky.models.Cliente;
import com.ecommerce.FerreViky.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/customer")
@AllArgsConstructor
@CrossOrigin("*")
@Tag(
        name = "Customer",
        description = "Operations for viewing, editing, and deactivating the authenticated customer's account"
)
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;


    @Operation(
            summary = "Get customer profile",
            description = "Retrieves the public account details associated with the user authenticated by JWT."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Customer retrieved successfully",
                    content = @Content(
                            schema = @Schema(implementation = ClientePublico.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User is not authenticated or credentials are invalid",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer does not exist",
                    content = @Content
            )
    })
    @GetMapping
    public ResponseEntity<ClientePublico> getCustomer(
            @AuthenticationPrincipal Cliente customer) {

        return ResponseEntity.ok(customerService.getCustomer(customer));
    }


    @Operation(
            summary = "Update customer profile",
            description = "Updates the authenticated customer's details. The current password is required to change the password."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Customer updated successfully",
                    content = @Content(
                            schema = @Schema(implementation = ClientePublico.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User is not authenticated or the current password is incorrect",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer does not exist",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The email address is already registered",
                    content = @Content
            )
    })
    @PutMapping
    public ResponseEntity<ClientePublico> updateCustomer(
            @AuthenticationPrincipal Cliente customer,
            @Valid @RequestBody ClienteEditarRequest request) {

        return ResponseEntity.ok(
                customerService.updateCustomer(customer, request)
        );
    }

    @Operation(
            summary = "Deactivate customer account",
            description = "Deactivates the authenticated customer's account without permanently deleting its record."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Customer deactivated successfully",
                    content = @Content(
                            schema = @Schema(
                                    type = "string",
                                    example = "Customer deactivated successfully"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User is not authenticated or credentials are invalid",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer does not exist",
                    content = @Content
            )
    })
    @DeleteMapping
    public ResponseEntity<String> deactivateCustomer(
            @AuthenticationPrincipal Cliente customer) {

        customerService.deactivateCustomer(customer);
        return ResponseEntity.ok("Customer deactivated successfully");
    }


    @Operation(
            summary = "Upload profile photo",
            description = "Uploads a profile photo for the authenticated customer (multipart field 'file'). Only JPG and PNG are accepted, with a 5 MB maximum. If a photo already exists, it is replaced and the previous one is deleted from the bucket."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo uploaded; returns the updated profile"),
            @ApiResponse(responseCode = "400", description = "The file is not a valid image or its format is not allowed (JPG and PNG only)"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @ApiResponse(responseCode = "404", description = "Customer does not exist"),
            @ApiResponse(responseCode = "413", description = "The image exceeds the maximum allowed size")
    })
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientePublico> uploadProfilePhoto(
            @AuthenticationPrincipal Cliente customer,
            @RequestParam("file") MultipartFile file) throws IOException {

        return ResponseEntity.ok(customerService.uploadCustomerPhoto(customer, file));
    }


    @Operation(summary = "Remove profile photo",
            description = "Deletes the authenticated customer's photo. If there is no photo, nothing happens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo deleted; returns the updated profile"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @ApiResponse(responseCode = "404", description = "Customer does not exist")
    })
    @DeleteMapping("/photo")
    public ResponseEntity<ClientePublico> removeProfilePhoto(@AuthenticationPrincipal Cliente customer) {
        return ResponseEntity.ok(customerService.removeCustomerPhoto(customer));
    }


}
