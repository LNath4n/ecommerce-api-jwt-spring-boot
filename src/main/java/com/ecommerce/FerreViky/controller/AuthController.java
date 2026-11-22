package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.AuthResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerCreatedResponse;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.ResendVerificationRequest;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.VerifyEmailRequest;
import com.ecommerce.FerreViky.dto.dtos.customer.CustomerDTO.CustomerLoginRequest;
import com.ecommerce.FerreViky.ratelimit.RateLimitType;
import com.ecommerce.FerreViky.ratelimit.provider.BucketProvider;
import com.ecommerce.FerreViky.service.AuthService;
import com.ecommerce.FerreViky.utils.EmailLanguage;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@CrossOrigin("*")
@Tag(name = "Customers", description = "Customer authentication and registration operations")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final BucketProvider bucketProvider;

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
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data (validation failed)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidEmail",
                                    summary = "Email with an invalid format",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"email\":\"Email format is invalid\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Email not verified yet. The client should offer to resend the verification email.",
                    content = @Content
            ),
            @ApiResponse(responseCode = "401", description = "Incorrect credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody CustomerLoginRequest dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(
            summary = "Register a new customer",
            description = "Creates a new customer account with an empty cart and sends a verification email. "
                    + "No JWT is returned: the customer must verify their email before logging in."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Customer registered. A verification email was sent.",
                    content = @Content(schema = @Schema(implementation = CustomerCreatedResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data (validation failed)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidEmail",
                                    summary = "Email with an invalid format",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"email\":\"Email format is invalid\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The email is already registered",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(
                                    type = "string",
                                    example = "A customer with email juan@gmail.com already exists"
                            )
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Internal error while creating the customer's cart", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CustomerCreatedResponse> registerCustomer(
            @Valid @RequestBody CustomerLoginRequest dto,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerCustomer(dto, EmailLanguage.from(acceptLanguage)));
    }

    @Operation(
            summary = "Verify email",
            description = "Confirms the customer's email with the single-use token received by mail."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Email verified", content = @Content),
            @ApiResponse(responseCode = "400", description = "Token is invalid, expired or already used", content = @Content)
    })
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest dto) {
        authService.verifyEmail(dto.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Resend verification email",
            description = "Sends a new verification email if the account exists and is not verified yet. "
                    + "Always responds the same way so it cannot be used to find out which emails are registered. "
                    + "Limited per IP (filter, answers 429) and per email (answers 202 without sending)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid email format", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP", content = @Content)
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest dto,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {

        String email = dto.email().trim().toLowerCase(Locale.ROOT);

        // Límite por email: evita bombardear el correo de una víctima desde muchas IPs.
        // Se hashea para no guardar correos en claro en Redis/memoria.
        Bucket bucket = bucketProvider.bucketFor(sha256(email), RateLimitType.RESEND_EMAIL);

        if (bucket.tryConsume(1)) {
            authService.resendVerification(email, EmailLanguage.from(acceptLanguage));
        } else {
            // Se responde igual (202) para no revelar nada; simplemente no se hace nada
            log.warn("Resend verification skipped: per-email rate limit reached");
        }

        return ResponseEntity.accepted().build();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado en toda JVM
            throw new IllegalStateException(e);
        }
    }
}