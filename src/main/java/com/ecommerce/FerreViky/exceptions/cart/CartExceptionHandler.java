package com.ecommerce.FerreViky.exceptions.cart;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CartExceptionHandler {

    @ExceptionHandler(CartExceptions.CartNotFoundException.class)
    public ResponseEntity<String> handleCartNotFound(CartExceptions.CartNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.CustomerCartNotFoundException.class)
    public ResponseEntity<String> handleCustomerCartNotFound(CartExceptions.CustomerCartNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.EmptyCartException.class)
    public ResponseEntity<String> handleEmptyCart(CartExceptions.EmptyCartException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.QuantityExceededException.class)
    public ResponseEntity<String> handleQuantityExceeded(CartExceptions.QuantityExceededException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.InvalidQuantityException.class)
    public ResponseEntity<String> handleInvalidQuantity(CartExceptions.InvalidQuantityException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.ProductNotFoundInCartException.class)
    public ResponseEntity<String> handleProductNotFoundInCart(CartExceptions.ProductNotFoundInCartException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.ProductAlreadyInCartException.class)
    public ResponseEntity<String> handleProductAlreadyInCart(CartExceptions.ProductAlreadyInCartException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.ProductNotInCartException.class)
    public ResponseEntity<String> handleProductNotInCart(CartExceptions.ProductNotInCartException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(CartExceptions.CartCreationException.class)
    public ResponseEntity<String> handleCartCreation(CartExceptions.CartCreationException ex){
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }
}
