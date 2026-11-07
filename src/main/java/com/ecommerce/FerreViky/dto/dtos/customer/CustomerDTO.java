package com.ecommerce.FerreViky.dto.dtos.customer;

import com.ecommerce.FerreViky.dto.validation.NoNulls.NoNullChars;
import com.ecommerce.FerreViky.dto.validation.sinHtml.SinHtml;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class CustomerDTO {
    @Schema(description = "Response after registering a new customer")
    public record CustomerCreatedResponse(
            @Schema(description = "Generated customer ID", example = "42")
            Long id,

            @Schema(description = "Registered email", example = "juan@gmail.com")
            String email
    ) {}

    @Schema(description = "Credentials used for both login and registration")
    public record CustomerLoginRequest(
            @Schema(description = "Customer email", example = "juan@gmail.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Email format is invalid")
            @NoNullChars
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

        public CustomerLoginRequest {
            email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        }

        @AssertTrue(message = "Password cannot contain only whitespace")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isPasswordNotOnlyWhitespace() {
            return password == null || password.codePoints().anyMatch(
                    cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
            );
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
    public record PublicCustomerResponse(
            @Schema(description = "Customer email", example = "juan@gmail.com")
            @Email(message = "Email format is invalid")
            String email,

            @Schema(description = "Customer name", example = "Juan Pérez")
            String name,

            @Schema(description = "Customer contact phone number", example = "2461234567")
            String phone,

            @Schema(description = "Customer photo URL", example = "https://cdn.example.com/customers/42.png")
            String photoUrl
    ) {}

    @Schema(description = "Data sent to update the customer's information")
    public record UpdateCustomerRequest(
            @Schema(description = "New customer email, if they want to change it (optional)",
                    example = "nuevo@gmail.com")
            @Email(message = "Email format is invalid")
            @NoNullChars
            @Size(max = 254, message = "Email cannot exceed 254 characters")
            @Pattern(
                    regexp = "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$",
                    message = "Email must have a valid domain"
            )
            String email,

            @Schema(description = "Current password, required to change the password (optional)",
                    example = "secreto123", maxLength = 72)
            @Size(max = 72, message = "Current password cannot exceed 72 characters")
            @NoNullChars
            String currentPassword,

            @Schema(description = "New customer password (optional)",
                    example = "NuevaClave123", minLength = 8, maxLength = 72)
            @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
            @NoNullChars
            String newPassword,

            @Schema(description = "New customer name (optional)", example = "Juan Pérez")
            @SinHtml
            @NoNullChars
            @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*$",
                    message = "Name cannot contain control or invisible characters")
            @Size(max = 100, message = "Name cannot exceed 100 characters")
            String name,

            @Schema(description = "New contact phone number (optional)", example = "2461234567")
            @Pattern(regexp = "[0-9]{10}",
                    message = "Phone must contain exactly 10 digits")
            String phone,
            @Schema(description = "If true, all existing sessions (tokens) are invalidated (optional, default false)",
                    example = "true")
            Boolean closeSessions
    ) {

        public UpdateCustomerRequest {
            email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
            name = name == null ? null : name.trim();
            phone = phone == null ? null
                    : phone.replaceAll("[\\s\\-().]", "");
        }

        @AssertTrue(message = "Current password is too long (max 72 bytes)")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isCurrentPasswordBytesValid() {
            return currentPassword == null
                    || currentPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @AssertTrue(message = "New password is too long (max 72 bytes)")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isNewPasswordBytesValid() {
            return newPassword == null
                    || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @AssertTrue(message = "Current password is required to change the password")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isCurrentPasswordPresentWhenChanging() {
            return newPassword == null
                    || (currentPassword != null && !currentPassword.isBlank());
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
        public boolean isNameNotBlankIfPresent() {
            return name == null || !name.isBlank();
        }

        @AssertTrue(message = "New password cannot contain only whitespace")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isNewPasswordNotOnlyWhitespace() {
            return newPassword == null || newPassword.codePoints().anyMatch(
                    cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
            );
        }
        @AssertTrue(message = "Phone cannot be blank")
        @Schema(hidden = true)
        @JsonIgnore
        public boolean isPhoneNotBlankIfPresent() {
            return phone == null || !phone.isBlank();
        }
    }
}