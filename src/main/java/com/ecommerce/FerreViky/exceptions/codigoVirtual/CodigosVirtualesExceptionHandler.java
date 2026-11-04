package com.ecommerce.FerreViky.exceptions.codigoVirtual;

import com.ecommerce.FerreViky.exceptions.codigoVirtual.CodigosVirtualesExceptions;
import com.ecommerce.FerreViky.exceptions.productos.ProductosExceptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice

public class CodigosVirtualesExceptionHandler {
    @ExceptionHandler(CodigosVirtualesExceptions.CodigosDuplicadosException.class)
    public ResponseEntity<String> handleCodigosDuplicados(CodigosVirtualesExceptions.CodigosDuplicadosException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
