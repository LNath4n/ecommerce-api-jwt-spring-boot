package com.ecommerce.FerreViky.service;

import com.ecommerce.FerreViky.cache.cacheEvict.EvictTopProductos;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewEligibility;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewRequest;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewResponse;
import com.ecommerce.FerreViky.dto.dtos.review.ReviewDtos.ReviewSummary;
import com.ecommerce.FerreViky.exceptions.product.ProductExceptions.ProductNotFoundException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.InvalidReviewException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.ProductNotPurchasedException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.ReviewAlreadyExistsException;
import com.ecommerce.FerreViky.exceptions.review.ReviewExceptions.ReviewNotFoundException;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.models.Orders.OrderStatus;
import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.models.Review;
import com.ecommerce.FerreViky.repository.ProductRepository;
import com.ecommerce.FerreViky.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles review creation, retrieval, updates, and deletion, including purchase
 * eligibility and product review statistics.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    /**
     * Creates a review for a product purchased by the customer.
     *
     * <p>The customer must have a paid order containing the product and must
     * not already have a review for it.</p>
     *
     * @param productId ID of the product to review
     * @param customer authenticated customer creating the review
     * @param req review rating and optional comment
     * @return the created review
     * @throws ProductNotFoundException if the product does not exist
     * @throws ProductNotPurchasedException if the customer has no paid order containing the product
     * @throws ReviewAlreadyExistsException if the customer has already reviewed the product
     * @throws InvalidReviewException if the rating or comment is invalid
     */
    @EvictTopProductos
    @Transactional
    public ReviewResponse create(Long productId, Customer customer, ReviewRequest req) {
        validate(req);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!reviewRepository.hasPurchased(productId, customer.getId(), OrderStatus.PAID))
            throw new ProductNotPurchasedException(productId);

        if (reviewRepository.existsByProductIdAndCustomerId(productId, customer.getId()))
            throw new ReviewAlreadyExistsException(productId);

        Review review = Review.builder()
                .product(product)
                .customer(customer)
                .rating(req.rating())
                .comment(cleanComment(req.comment()))
                .build();

        try {
            ReviewResponse response = ReviewResponse.from(reviewRepository.saveAndFlush(review));
            productRepository.refreshReviewStats(productId);
            return response;
        } catch (DataIntegrityViolationException e) {
            throw new ReviewAlreadyExistsException(productId);
        }
    }

    /**
     * Updates the authenticated customer's review for a product.
     *
     * @param productId ID of the product whose review is being updated
     * @param customer authenticated customer who owns the review
     * @param req updated rating and optional comment
     * @return the updated review
     * @throws ReviewNotFoundException if the customer has no review for the product
     * @throws InvalidReviewException if the rating or comment is invalid
     */
    @EvictTopProductos
    @Transactional
    public ReviewResponse update(Long productId, Customer customer, ReviewRequest req) {
        validate(req);

        Review review = reviewRepository.findByProductIdAndCustomerId(productId, customer.getId())
                .orElseThrow(() -> new ReviewNotFoundException(productId));

        review.setRating(req.rating());
        review.setComment(cleanComment(req.comment()));
        ReviewResponse response = ReviewResponse.from(review);
        productRepository.refreshReviewStats(productId); // flushAutomatically guarda el cambio antes
        return response;
    }

    /**
     * Deletes the authenticated customer's review for a product.
     *
     * @param productId ID of the product whose review is being deleted
     * @param customer authenticated customer who owns the review
     * @throws ReviewNotFoundException if the customer has no review for the product
     */
    @EvictTopProductos
    @Transactional
    public void delete(Long productId, Customer customer) {
        Review review = reviewRepository.findByProductIdAndCustomerId(productId, customer.getId())
                .orElseThrow(() -> new ReviewNotFoundException(productId));
        reviewRepository.delete(review);
        productRepository.refreshReviewStats(productId);
    }

    /**
     * Retrieves the authenticated customer's review for a product.
     *
     * @param productId ID of the product
     * @param customer authenticated customer whose review is requested
     * @return the customer's review
     * @throws ReviewNotFoundException if the customer has no review for the product
     */
    @Transactional(readOnly = true)
    public ReviewResponse getMine(Long productId, Customer customer) {
        return reviewRepository.findByProductIdAndCustomerId(productId, customer.getId())
                .map(ReviewResponse::from)
                .orElseThrow(() -> new ReviewNotFoundException(productId));
    }

    /**
     * Returns a paginated list of reviews for a product.
     *
     * @param productId ID of the product
     * @param pageable pagination and sorting settings
     * @return the requested page of reviews
     * @throws ProductNotFoundException if the product does not exist
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> list(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId))
            throw new ProductNotFoundException(productId);
        return reviewRepository.findByProductId(productId, pageable).map(ReviewResponse::from);
    }

    /**
     * Calculates the product's average rating and review count.
     *
     * @param productId ID of the product
     * @return review count and average rating rounded to one decimal place
     * @throws ProductNotFoundException if the product does not exist
     */
    @Transactional(readOnly = true)
    public ReviewSummary summary(Long productId) {
        if (!productRepository.existsById(productId))
            throw new ProductNotFoundException(productId);

        long count = reviewRepository.countByProductId(productId);
        Double avg = reviewRepository.findAverageRating(productId);
        double rounded = avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
        return new ReviewSummary(productId, rounded, count);
    }

    /**
     * Determines whether the customer may review the product.
     *
     * @param productId ID of the product
     * @param customer authenticated customer whose eligibility is checked
     * @return purchase and review status used to determine review eligibility
     */
    @Transactional(readOnly = true)
    public ReviewEligibility eligibility(Long productId, Customer customer) {
        boolean purchased = reviewRepository.hasPurchased(productId, customer.getId(), OrderStatus.PAID);
        boolean reviewed = reviewRepository.existsByProductIdAndCustomerId(productId, customer.getId());
        return new ReviewEligibility(purchased, reviewed);
    }

    // helpers

    /**
     * Validates review fields independently of request-layer validation.
     *
     * @param req review request to validate
     * @throws InvalidReviewException if the request, rating, or comment is invalid
     */
    private void validate(ReviewRequest req) {
        if (req == null || req.rating() == null || req.rating() < 1 || req.rating() > 5)
            throw new InvalidReviewException("La calificación debe estar entre 1 y 5");
        if (req.comment() != null && req.comment().strip().length() > MAX_COMMENT_LENGTH)
            throw new InvalidReviewException("El comentario no puede pasar de " + MAX_COMMENT_LENGTH + " caracteres");
    }

    /**
     * Trims surrounding whitespace and converts blank comments to {@code null}.
     *
     * @param comment raw review comment
     * @return the trimmed comment, or {@code null} if it is blank
     */
    private String cleanComment(String comment) {
        if (comment == null) return null;
        String c = comment.strip();
        return c.isEmpty() ? null : c;
    }

}