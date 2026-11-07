package com.ecommerce.FerreViky.exceptions.customer;

public class CustomerExceptions {

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String email) {
            super("A customer with email " + email + " already exists");
        }

        public EmailAlreadyExistsException(String email, Throwable cause) {
            super("A customer with email " + email + " already exists", cause);
        }
    }

    public static class CustomerNotFoundException extends RuntimeException {
        public CustomerNotFoundException(String email) {
            super("Customer not found with email " + email);
        }

        public CustomerNotFoundException(Long id) {
            super("Customer not found with ID " + id);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid credentials");
        }
    }

    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException() {
            super("The provided password is invalid");
        }
    }

    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    public static class EmailAlreadyOwnedException extends RuntimeException {
        public EmailAlreadyOwnedException(String message) {
            super(message);
        }
    }

    public static class CustomerModifiedException extends RuntimeException {
        public CustomerModifiedException(Long id, Throwable cause) {
            super("Customer with ID " + id + " was modified by another operation", cause);
        }
    }
}