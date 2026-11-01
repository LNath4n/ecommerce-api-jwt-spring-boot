package com.ecommerce.FerreViky.exceptions;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "message", message
        ));
    }

    // Malformed, unreadable or wrongly-typed JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJson(HttpMessageNotReadableException ex) {
        // Detail goes to the server log only, never to the client
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return badRequest("Request body is invalid");
    }

    // @Valid on @RequestBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));

        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "message", "Validation failed",
                "errors", errors
        ));
    }

    // Constraints on simple params (@Positive, @NotBlank, etc.)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return badRequest(msg);
    }

    // Wrong type (String where a Long is expected, etc.)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Parameter '" + ex.getName() + "' has an invalid format");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> multipartInvalido(MultipartException e) {
        Throwable raiz = NestedExceptionUtils.getMostSpecificCause(e);

        if (raiz instanceof IOException && !(raiz instanceof FileUploadException)) {
            // fallo del servidor (disco, I/O): no es culpa del cliente
            log.error("Error de I/O procesando multipart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al procesar el archivo"));
        }

        log.warn("Multipart inválido: {}", raiz.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Petición multipart inválida"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> demasiadoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "La imagen supera el tamaño máximo permitido"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> {
            String param = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().stream()
                    .findFirst()
                    .ifPresent(e -> errors.putIfAbsent(param, e.getDefaultMessage()));
        });

        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "message", "Validation failed",
                "errors", errors
        ));
    }

    // Falta un parámetro requerido
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("Parameter '" + ex.getParameterName() + "' is required");
    }
}