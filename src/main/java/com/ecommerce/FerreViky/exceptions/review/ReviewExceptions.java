package com.ecommerce.FerreViky.exceptions.review;

public class ReviewExceptions {

    public static class ProductNotPurchasedException extends RuntimeException {
        public ProductNotPurchasedException(Long productId) {
            super("You can only review products you have purchased (product " + productId + ")");
        }
    }

    public static class ReviewAlreadyExistsException extends RuntimeException {
        public ReviewAlreadyExistsException(Long productId) {
            super("You have already reviewed product " + productId + ". Edit your review instead of creating another one");
        }
    }

    public static class ReviewNotFoundException extends RuntimeException {
        public ReviewNotFoundException(Long productId) {
            super("You don't have a review for product " + productId);
        }
    }

    public static class InvalidReviewException extends RuntimeException {
        public InvalidReviewException(String message) {
            super(message);
        }
    }
}