package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerLoginRequest;
import com.ecommerce.FerreViky.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@CrossOrigin("*")
@Tag(name = "Customers", description = "Customer authentication and registration operations")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Log in",
            description = "Authenticates the customer with their email and password. Returns a JWT for use with protected endpoints."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful. Returns the JWT.",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid input data (validation failed)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Incorrect credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody CustomerLoginRequest dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(
            summary = "Register a new customer",
            description = "Creates a new customer account and automatically assigns an empty cart. Returns a JWT ready to use."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Customer registered successfully.",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid input data (validation failed)", content = @Content),
            @ApiResponse(responseCode = "409", description = "The email is already registered", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal error while creating the customer's cart", content = @Content)
    })
    @PostMapping
    public ResponseEntity<AuthResponse> registerCustomer(@Valid @RequestBody CustomerLoginRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCustomer(dto));
    }
}