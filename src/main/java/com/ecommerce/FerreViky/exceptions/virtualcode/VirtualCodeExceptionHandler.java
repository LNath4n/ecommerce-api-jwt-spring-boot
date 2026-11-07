package com.ecommerce.FerreViky.exceptions.virtualcode;

import com.ecommerce.FerreViky.exceptions.virtualcode.VirtualCodeExceptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice

public class VirtualCodeExceptionHandler {
    @ExceptionHandler(VirtualCodeExceptions.DuplicateCodesException.class)
    public ResponseEntity<String> handleDuplicateCodes(VirtualCodeExceptions.DuplicateCodesException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
