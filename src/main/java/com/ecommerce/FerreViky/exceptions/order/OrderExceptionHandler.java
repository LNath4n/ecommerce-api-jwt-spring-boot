package com.ecommerce.FerreViky.exceptions.order;

import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.OrderNotFoundException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.OrderNotPayableException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.PaymentNotCompletedException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.PaymentProcessingException;
import com.ecommerce.FerreViky.exceptions.order.OrderExceptions.ReservationExpiredException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(OrderNotPayableException.class)
    public ResponseEntity<String> handleOrderNotPayable(OrderNotPayableException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<String> handleReservationExpired(ReservationExpiredException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(PaymentNotCompletedException.class)
    public ResponseEntity<String> handlePaymentNotCompleted(PaymentNotCompletedException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<String> handlePaymentProcessing(PaymentProcessingException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
