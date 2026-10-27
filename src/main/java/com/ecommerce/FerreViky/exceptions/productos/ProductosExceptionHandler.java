package com.ecommerce.FerreViky.exceptions.productos;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ProductosExceptionHandler {

    @ExceptionHandler(ProductosExceptions.ProductoNoEncontradoException.class)
    public ResponseEntity<String> handleProductoNoEncontrado(ProductosExceptions.ProductoNoEncontradoException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(ProductosExceptions.GrupoNoEncontradoException.class)
    public ResponseEntity<String> handleGrupoDeProductoNoEncontrado(ProductosExceptions.GrupoNoEncontradoException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(ProductosExceptions.CategoriaNoEncontradaException.class)
    public ResponseEntity<String> handleCategoriaNoEncontrada(ProductosExceptions.CategoriaNoEncontradaException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(ProductosExceptions.ImagenInvalidaException.class)
    public ResponseEntity<String> handleImagenInvalida(ProductosExceptions.ImagenInvalidaException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(ProductosExceptions.SkuDuplicadoException.class)
    public ResponseEntity<String> handleSkuDuplicado(ProductosExceptions.SkuDuplicadoException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(ProductosExceptions.ProductoConDependenciasException.class)
    public ResponseEntity<String> handleSkuDuplicado(ProductosExceptions.ProductoConDependenciasException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
