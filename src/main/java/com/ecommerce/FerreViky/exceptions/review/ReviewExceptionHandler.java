package com.ecommerce.FerreViky.exceptions.review;

import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.InvalidReviewException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.ProductNotPurchasedException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.ReviewAlreadyExistsException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.ReviewNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReviewExceptionHandler {

    @ExceptionHandler(InvalidReviewException.class)
    public ResponseEntity<String> handleInvalidReview(InvalidReviewException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(ProductNotPurchasedException.class)
    public ResponseEntity<String> handleProductNotPurchased(ProductNotPurchasedException ex) {
        return ResponseEntity.status(403).body(ex.getMessage());
    }

    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<String> handleReviewAlreadyExists(ReviewAlreadyExistsException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<String> handleReviewNotFound(ReviewNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }
}
