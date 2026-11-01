package com.ecommerce.FerreViky.exceptions.categorias;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CategoriasExceptionsHandler {

    @ExceptionHandler(CategoriasExceptions.CategoriaDuplicadaException.class)
    public ResponseEntity<String> handleCategoriaDuplicada(CategoriasExceptions.CategoriaDuplicadaException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(CategoriasExceptions.CategoriaConProductosException.class)
    public ResponseEntity<String> handleCategoriaConProductos(CategoriasExceptions.CategoriaConProductosException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}