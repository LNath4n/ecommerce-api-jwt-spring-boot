package com.ecommerce.FerreViky.exceptions.cart;

public class CartExceptions {

    public static class QuantityExceededException extends RuntimeException {
        public QuantityExceededException(Integer availableStock, Integer quantity, String productName) {
            super("Cannot add " + quantity + " units of " + productName
                    + "; only " + availableStock + " are in stock");
        }
        public QuantityExceededException(long availableStock, long quantity, String productName) {
            super("Cannot have " + quantity + " units of " + productName
                    + "; only " + availableStock + " are in stock");
        }

        public QuantityExceededException(String productName, long availableStock, long cartQuantity,
                                         long requestedQuantity) {
            super(cartQuantity == 0
                    ? "Cannot add " + requestedQuantity + " units of " + productName
                    + "; only " + availableStock + " are in stock"
                    : "You already have " + cartQuantity + " of " + productName + " in your cart and only "
                    + availableStock + " are in stock. You can add "
                    + Math.max(0, availableStock - cartQuantity) + " more");
        }

        public QuantityExceededException(String productName, long availableStock, long desiredQuantity) {
            super("Cannot have " + desiredQuantity + " units of " + productName
                    + "; only " + availableStock + " are in stock");
        }
    }



    public static class CartNotFoundException extends RuntimeException {
        public CartNotFoundException(Long id) {
            super("Cart not found: " + id);
        }
    }

    public static class CustomerCartNotFoundException extends RuntimeException {
        public CustomerCartNotFoundException(Long id) {
            super("No cart found for customer " + id);
        }
    }

    public static class EmptyCartException extends RuntimeException {
        public EmptyCartException() {
            super("The cart is empty");
        }
    }

    public static class InvalidQuantityException extends RuntimeException {
        public InvalidQuantityException(Integer quantity) {
            super("Invalid quantity: " + quantity);
        }
    }

    public static class ProductNotFoundInCartException extends RuntimeException {
        public ProductNotFoundInCartException(Long productId) {
            super("Product not found with ID: " + productId);
        }
    }

    public static class ProductAlreadyInCartException extends RuntimeException {
        public ProductAlreadyInCartException(Long productId) {
            super("Product with ID " + productId + " is already in the cart");
        }
    }

    public static class ProductNotInCartException extends RuntimeException {
        public ProductNotInCartException(Long productId) {
            super("Product with ID " + productId + " is not in the cart");
        }
    }

    public static class CartCreationException extends RuntimeException {
        public CartCreationException(Long customerId, Throwable cause) {
            super("Could not create a cart for customer with ID: " + customerId, cause);
        }
    }
    public static class ConcurrentCartUpdateException extends RuntimeException {
        public ConcurrentCartUpdateException(String message) { super(message); }
    }
}
