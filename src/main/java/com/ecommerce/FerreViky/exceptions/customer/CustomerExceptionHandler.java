package com.ecommerce.FerreViky.exceptions.customer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class CustomerExceptionHandler {
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        // log.warn("Login failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Bad credentials"));
    }

    @ExceptionHandler(CustomerExceptions.EmailAlreadyExistsException.class)
    public ResponseEntity<String> handleEmailAlreadyExists(CustomerExceptions.EmailAlreadyExistsException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(CustomerExceptions.CustomerNotFoundException.class)
    public ResponseEntity<String> handleCustomerNotFound(CustomerExceptions.CustomerNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }


    @ExceptionHandler(CustomerExceptions.InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(CustomerExceptions.InvalidCredentialsException ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(CustomerExceptions.InvalidPasswordException.class)
    public ResponseEntity<String> handleInvalidPassword(CustomerExceptions.InvalidPasswordException ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(CustomerExceptions.InvalidImageException.class)
    public ResponseEntity<String> handleInvalidImage(CustomerExceptions.InvalidImageException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(CustomerExceptions.EmailAlreadyOwnedException.class)
    public ResponseEntity<String> handleEmailAlreadyOwned(CustomerExceptions.EmailAlreadyOwnedException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
