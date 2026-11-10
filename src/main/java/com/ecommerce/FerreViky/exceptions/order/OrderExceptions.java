package com.ecommerce.FerreViky.exceptions.order;

import com.ecommerce.FerreViky.models.Orders.OrderStatus;

public class OrderExceptions {

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException() {
            super("Order not found");
        }

        public OrderNotFoundException(Long orderId) {
            super("Order not found with ID: " + orderId);
        }
    }

    public static class OrderNotPayableException extends RuntimeException {
        public OrderNotPayableException(Long orderId, OrderStatus status) {
            super("Order " + orderId + " cannot be paid because its status is " + status);
        }
    }

    public static class ReservationExpiredException extends RuntimeException {
        public ReservationExpiredException(Long orderId) {
            super("The reservation for order " + orderId + " has expired");
        }
    }

    public static class PaymentNotCompletedException extends RuntimeException {
        public PaymentNotCompletedException() {
            super("The payment was not completed");
        }
    }

    public static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(Long orderId) {
            super("Payment for order " + orderId + " is still being processed");
        }
    }
}
