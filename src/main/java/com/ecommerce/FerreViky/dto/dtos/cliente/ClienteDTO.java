package com.ecommerce.FerreViky.dto.dtos.cliente;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.nio.charset.StandardCharsets;

public class ClienteDTO {
    @Schema(description = "Response after registering a new customer")
    public record CreacionDeClienteRespuestaDto(
            @Schema(description = "Generated customer ID", example = "42")
            Long id,

            @Schema(description = "Registered email", example = "juan@gmail.com")
            String email
    ) {}

    @Schema(description = "Credentials used for both login and registration")
    public record LoginClienteDto(
            @Schema(description = "Customer email", example = "juan@gmail.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Email format is invalid")
            @Size(max = 254, message = "Email cannot exceed 254 characters")
            @Pattern(
                    regexp = "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$",
                    message = "Email must have a valid domain"
            )
            String email,

            @Schema(description = "Plain-text password", example = "secreto123", minLength = 8, maxLength = 72)
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            String password
    ) {

        public LoginClienteDto {
            email = email == null ? null : email.trim();
        }

        @AssertTrue(message = "Password is too long (max 72 bytes)")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isPasswordBytesValid() {
            return password == null
                    || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }

    @Schema(description = "Customer access token")
    public record AuthResponse(
            @Schema(description = "JWT authentication token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String token
    ) {}

    @Schema(description = "Public customer information, without credentials")
    public record ClientePublico(
            @Schema(description = "Customer email", example = "juan@gmail.com")
            @Email(message = "Email format is invalid")
            String email,

            @Schema(description = "Customer name", example = "Juan Pérez")
            String nombre,

            @Schema(description = "Customer contact phone number", example = "2461234567")
            String telefono,

            @Schema(description = "Customer photo URL", example = "https://cdn.example.com/customers/42.png")
            String fotoUrl
    ) {}

    @Schema(description = "Data sent to update the customer's information")
    public record ClienteEditarRequest(
            @Schema(description = "New customer email, if they want to change it (optional)",
                    example = "nuevo@gmail.com")
            @Email(message = "Email format is invalid")
            @Size(max = 254, message = "Email cannot exceed 254 characters")
            String email,

            @Schema(description = "Current password, required to change the password (optional)",
                    example = "secreto123", maxLength = 72)
            @Size(max = 72, message = "Current password cannot exceed 72 characters")
            String passwordVieja,

            @Schema(description = "New customer password (optional)",
                    example = "NuevaClave123", minLength = 8, maxLength = 72)
            @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
            String passwordNueva,

            @Schema(description = "New customer name (optional)", example = "Juan Pérez")
            @Size(max = 100, message = "Name cannot exceed 100 characters")
            String nombre,

            @Schema(description = "New contact phone number (optional)", example = "2461234567")
            @Pattern(regexp = "[0-9]{10}",
                    message = "Phone must contain exactly 10 digits")
            String telefono
    ) {

        @AssertTrue(message = "Current password is too long (max 72 bytes)")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isPasswordViejaBytesValid() {
            return passwordVieja == null
                    || passwordVieja.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @AssertTrue(message = "New password is too long (max 72 bytes)")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isPasswordNuevaBytesValid() {
            return passwordNueva == null
                    || passwordNueva.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @AssertTrue(message = "Current password is required to change the password")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isPasswordViejaPresenteSiCambia() {
            return passwordNueva == null
                    || (passwordVieja != null && !passwordVieja.isBlank());
        }

        @AssertTrue(message = "Email cannot be blank")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isEmailNotBlankIfPresent() {
            return email == null || !email.isBlank();
        }

        @AssertTrue(message = "Name cannot be blank")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isNombreNotBlankIfPresent() {
            return nombre == null || !nombre.isBlank();
        }

        @AssertTrue(message = "Phone cannot be blank")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isTelefonoNotBlankIfPresent() {
            return telefono == null || !telefono.isBlank();
        }
    }
}