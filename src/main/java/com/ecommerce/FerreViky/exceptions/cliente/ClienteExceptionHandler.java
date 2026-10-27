package com.ecommerce.FerreViky.exceptions.cliente;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ClienteExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales inválidas"));
    }

    @ExceptionHandler(ClienteExceptions.EmailYaExisteException.class)
    public ResponseEntity<String> handleEmailYaExiste(ClienteExceptions.EmailYaExisteException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(ClienteExceptions.ClienteNoEncontradoException.class)
    public ResponseEntity<String> handleClienteNoEncontrado(ClienteExceptions.ClienteNoEncontradoException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }


    @ExceptionHandler(ClienteExceptions.CredencialesInvalidasException.class)
    public ResponseEntity<String> handleCredencialesInvalidas(ClienteExceptions.CredencialesInvalidasException ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(ClienteExceptions.ContrasenaInvalida.class)
    public ResponseEntity<String> handleContrasenaInvalida(ClienteExceptions.ContrasenaInvalida ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(ClienteExceptions.ImagenInvalidaException.class)
    public ResponseEntity<String> handleImagenInvalida(ClienteExceptions.ImagenInvalidaException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(ClienteExceptions.CorreoYaEsTuyo.class)
    public ResponseEntity<String> handleCorreoYaEsTuyo(ClienteExceptions.CorreoYaEsTuyo ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
