package com.ecommerce.FerreViky.service;


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
import com.ecommerce.FerreViky.utils.CustomerFakes;
import com.ecommerce.FerreViky.utils.ProductFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private ReviewService reviewService;

    private Product product;
    private Customer customer;

    @BeforeEach
    void setUp() {
        product = ProductFakes.fakeProduct();
        customer = CustomerFakes.fakeCustomer();
    }

    // ---------- helpers ----------

    private Review fakeReview(int rating, String comment) {
        return Review.builder()
                .id(1L)
                .product(product)
                .customer(customer)
                .rating(rating)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** Deja pasar todas las reglas de negocio de create() para poder probar el resto. */
    private void stubCreateHappyPath() {
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(reviewRepository.hasPurchased(product.getId(), customer.getId(), OrderStatus.PAID))
                .thenReturn(true);
        when(reviewRepository.existsByProductIdAndCustomerId(product.getId(), customer.getId()))
                .thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // 
    // create
    //

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates the review and returns the response with the correct data")
        void createsReview() {
            stubCreateHappyPath();

            ReviewResponse response = reviewService.create(
                    product.getId(), customer, new ReviewRequest(5, "Excelente"));

            assertEquals(product.getId(), response.productId());
            assertEquals(5, response.rating());
            assertEquals("Excelente", response.comment());
        }

        @Test
        @DisplayName("saves the entity with product, customer, rating, and comment")
        void savesEntityWithCorrectData() {
            stubCreateHappyPath();

            reviewService.create(product.getId(), customer, new ReviewRequest(4, "Bueno"));

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).saveAndFlush(captor.capture());
            Review saved = captor.getValue();
            assertSame(product, saved.getProduct());
            assertSame(customer, saved.getCustomer());
            assertEquals(4, saved.getRating());
            assertEquals("Bueno", saved.getComment());
        }

        @Test
        @DisplayName("only PAID orders count when validating the purchase")
        void checksPurchaseWithPaidStatus() {
            stubCreateHappyPath();

            reviewService.create(product.getId(), customer, new ReviewRequest(3, null));

            verify(reviewRepository).hasPurchased(product.getId(), customer.getId(), OrderStatus.PAID);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("accepts ratings in the 1..5 range")
        void acceptsValidRatings(int rating) {
            stubCreateHappyPath();

            ReviewResponse response = reviewService.create(
                    product.getId(), customer, new ReviewRequest(rating, null));

            assertEquals(rating, response.rating());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 6, 100})
        @DisplayName("rejects null or out-of-range ratings without accessing repositories")
        void rejectsInvalidRatings(Integer rating) {
            InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                    () -> reviewService.create(product.getId(), customer, new ReviewRequest(rating, "x")));

            assertEquals("La calificación debe estar entre 1 y 5", ex.getMessage());
            verifyNoInteractions(productRepository, reviewRepository);
        }

        @Test
        @DisplayName("rejects a null request")
        void rejectsNullRequest() {
            assertThrows(InvalidReviewException.class,
                    () -> reviewService.create(product.getId(), customer, null));
            verifyNoInteractions(productRepository, reviewRepository);
        }

        @Test
        @DisplayName("rejects comments longer than 1000 characters")
        void rejectsTooLongComment() {
            String comment = "a".repeat(1001);

            InvalidReviewException ex = assertThrows(InvalidReviewException.class,
                    () -> reviewService.create(product.getId(), customer, new ReviewRequest(5, comment)));

            assertTrue(ex.getMessage().contains("1000"));
            verifyNoInteractions(productRepository, reviewRepository);
        }

        @Test
        @DisplayName("accepts a comment of exactly 1000 characters")
        void acceptsCommentOfExactly1000() {
            stubCreateHappyPath();
            String comment = "a".repeat(1000);

            ReviewResponse response = reviewService.create(
                    product.getId(), customer, new ReviewRequest(5, comment));

            assertEquals(comment, response.comment());
        }

        @Test
        @DisplayName("checks the 1000-character limit after trimming surrounding whitespace")
        void lengthLimitIgnoresSurroundingWhitespace() {
            stubCreateHappyPath();
            String comment = "   " + "a".repeat(1000) + "   ";

            ReviewResponse response = reviewService.create(
                    product.getId(), customer, new ReviewRequest(5, comment));

            assertEquals("a".repeat(1000), response.comment());
        }

        @Test
        @DisplayName("trims comment whitespace before saving")
        void stripsComment() {
            stubCreateHappyPath();

            ReviewResponse response = reviewService.create(
                    product.getId(), customer, new ReviewRequest(5, "  muy bueno  "));

            assertEquals("muy bueno", response.comment());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\n\t "})
        @DisplayName("stores null, empty, or whitespace-only comments as null")
        void blankCommentIsStoredAsNull(String comment) {
            stubCreateHappyPath();

            ReviewResponse response = reviewService.create(
                    product.getId(), customer, new ReviewRequest(5, comment));

            assertNull(response.comment());
            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).saveAndFlush(captor.capture());
            assertNull(captor.getValue().getComment());
        }

        @Test
        @DisplayName("throws ProductNotFoundException if the product does not exist")
        void productNotFound() {
            when(productRepository.findById(product.getId())).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class,
                    () -> reviewService.create(product.getId(), customer, new ReviewRequest(5, "ok")));

            verifyNoInteractions(reviewRepository);
        }

        @Test
        @DisplayName("throws ProductNotPurchasedException if the customer has not purchased the product")
        void notPurchased() {
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(reviewRepository.hasPurchased(product.getId(), customer.getId(), OrderStatus.PAID))
                    .thenReturn(false);

            assertThrows(ProductNotPurchasedException.class,
                    () -> reviewService.create(product.getId(), customer, new ReviewRequest(5, "ok")));

            verify(reviewRepository, never()).existsByProductIdAndCustomerId(any(), any());
            verify(reviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("throws ReviewAlreadyExistsException if a review already exists")
        void alreadyExists() {
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(reviewRepository.hasPurchased(product.getId(), customer.getId(), OrderStatus.PAID))
                    .thenReturn(true);
            when(reviewRepository.existsByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(true);

            assertThrows(ReviewAlreadyExistsException.class,
                    () -> reviewService.create(product.getId(), customer, new ReviewRequest(5, "ok")));

            verify(reviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("translates a concurrent unique-constraint violation to ReviewAlreadyExistsException")
        void raceConditionIsTranslated() {
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(reviewRepository.hasPurchased(product.getId(), customer.getId(), OrderStatus.PAID))
                    .thenReturn(true);
            when(reviewRepository.existsByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(false);
            when(reviewRepository.saveAndFlush(any(Review.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_review_product_customer"));

            assertThrows(ReviewAlreadyExistsException.class,
                    () -> reviewService.create(product.getId(), customer, new ReviewRequest(5, "ok")));
        }
    }

    // 
    // update
    // 
    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates the rating and comment on the customer's own review")
        void updatesOwnReview() {
            Review existing = fakeReview(2, "meh");
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.of(existing));

            ReviewResponse response = reviewService.update(
                    product.getId(), customer, new ReviewRequest(5, "  ahora sí  "));

            assertEquals(5, existing.getRating());
            assertEquals("ahora sí", existing.getComment());
            assertEquals(5, response.rating());
            assertEquals("ahora sí", response.comment());
            // dirty checking: no se llama a save explícitamente
            verify(reviewRepository, never()).save(any());
            verify(reviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("an empty comment clears the comment (null)")
        void blankCommentClearsComment() {
            Review existing = fakeReview(3, "algo");
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.of(existing));

            ReviewResponse response = reviewService.update(
                    product.getId(), customer, new ReviewRequest(3, "   "));

            assertNull(existing.getComment());
            assertNull(response.comment());
        }

        @Test
        @DisplayName("throws ReviewNotFoundException if the customer has no review")
        void notFound() {
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(ReviewNotFoundException.class,
                    () -> reviewService.update(product.getId(), customer, new ReviewRequest(4, "x")));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {0, 6})
        @DisplayName("rejects an invalid rating without querying the repository")
        void invalidRating(Integer rating) {
            assertThrows(InvalidReviewException.class,
                    () -> reviewService.update(product.getId(), customer, new ReviewRequest(rating, "x")));

            verifyNoInteractions(reviewRepository);
        }

        @Test
        @DisplayName("rejects an overlong comment")
        void tooLongComment() {
            assertThrows(InvalidReviewException.class,
                    () -> reviewService.update(product.getId(), customer,
                            new ReviewRequest(4, "a".repeat(1001))));

            verifyNoInteractions(reviewRepository);
        }
    }

    // 
    // delete
    // 
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes the customer's own review")
        void deletesOwnReview() {
            Review existing = fakeReview(4, "ok");
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.of(existing));

            reviewService.delete(product.getId(), customer);

            verify(reviewRepository).delete(existing);
        }

        @Test
        @DisplayName("throws ReviewNotFoundException and deletes nothing if no review exists")
        void notFound() {
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(ReviewNotFoundException.class,
                    () -> reviewService.delete(product.getId(), customer));

            verify(reviewRepository, never()).delete(any());
        }
    }

    // 
    // getMine
    // 
    @Nested
    @DisplayName("getMine")
    class GetMine {

        @Test
        @DisplayName("returns the customer's own review")
        void returnsOwnReview() {
            Review existing = fakeReview(4, "bien");
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.of(existing));

            ReviewResponse response = reviewService.getMine(product.getId(), customer);

            assertEquals(existing.getId(), response.id());
            assertEquals(product.getId(), response.productId());
            assertEquals(4, response.rating());
            assertEquals("bien", response.comment());
            assertEquals(existing.getCreatedAt(), response.createdAt());
        }

        @Test
        @DisplayName("throws ReviewNotFoundException if no review exists")
        void notFound() {
            when(reviewRepository.findByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(ReviewNotFoundException.class,
                    () -> reviewService.getMine(product.getId(), customer));
        }
    }

    // 
    // list
    // 
    @Nested
    @DisplayName("list")
    class ListReviews {

        @Test
        @DisplayName("returns the page mapped to ReviewResponse while preserving pagination")
        void returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Review r1 = fakeReview(5, "genial");
            Review r2 = fakeReview(3, null);
            Page<Review> page = new PageImpl<>(List.of(r1, r2), pageable, 2);

            when(productRepository.existsById(product.getId())).thenReturn(true);
            when(reviewRepository.findByProductId(product.getId(), pageable)).thenReturn(page);

            Page<ReviewResponse> result = reviewService.list(product.getId(), pageable);

            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
            assertEquals(5, result.getContent().get(0).rating());
            assertEquals("genial", result.getContent().get(0).comment());
            assertEquals(3, result.getContent().get(1).rating());
            assertNull(result.getContent().get(1).comment());
        }

        @Test
        @DisplayName("returns an empty page if the product has no reviews")
        void emptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.existsById(product.getId())).thenReturn(true);
            when(reviewRepository.findByProductId(product.getId(), pageable)).thenReturn(Page.empty(pageable));

            Page<ReviewResponse> result = reviewService.list(product.getId(), pageable);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("throws ProductNotFoundException if the product does not exist")
        void productNotFound() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.existsById(product.getId())).thenReturn(false);

            assertThrows(ProductNotFoundException.class,
                    () -> reviewService.list(product.getId(), pageable));

            verifyNoInteractions(reviewRepository);
        }
    }

    // 
    // summary
    // 
    @Nested
    @DisplayName("summary")
    class Summary {

        @Test
        @DisplayName("returns 0.0 and 0 when there are no reviews (null average)")
        void noReviews() {
            when(productRepository.existsById(product.getId())).thenReturn(true);
            when(reviewRepository.countByProductId(product.getId())).thenReturn(0L);
            when(reviewRepository.findAverageRating(product.getId())).thenReturn(null);

            ReviewSummary summary = reviewService.summary(product.getId());

            assertEquals(product.getId(), summary.productId());
            assertEquals(0.0, summary.average());
            assertEquals(0L, summary.count());
        }

        @ParameterizedTest
        @CsvSource({
                "4.0,    4.0",
                "3.666,  3.7",
                "4.24,   4.2",
                "4.25,   4.3",
                "1.0,    1.0",
                "4.9999, 5.0"
        })
        @DisplayName("rounds the average to one decimal place")
        void roundsToOneDecimal(double raw, double expected) {
            when(productRepository.existsById(product.getId())).thenReturn(true);
            when(reviewRepository.countByProductId(product.getId())).thenReturn(3L);
            when(reviewRepository.findAverageRating(product.getId())).thenReturn(raw);

            ReviewSummary summary = reviewService.summary(product.getId());

            assertEquals(expected, summary.average(), 0.0001);
            assertEquals(3L, summary.count());
        }

        @Test
        @DisplayName("throws ProductNotFoundException if the product does not exist")
        void productNotFound() {
            when(productRepository.existsById(product.getId())).thenReturn(false);

            assertThrows(ProductNotFoundException.class,
                    () -> reviewService.summary(product.getId()));

            verifyNoInteractions(reviewRepository);
        }
    }

    // 
    // eligibility
    // 
    @Nested
    @DisplayName("eligibility")
    class Eligibility {

        @ParameterizedTest(name = "purchased={0}, reviewed={1} -> canReview={2}")
        @CsvSource({
                "true,  false, true",
                "true,  true,  false",
                "false, false, false",
                "false, true,  false"
        })
        void allCombinations(boolean purchased, boolean reviewed, boolean canReview) {
            when(reviewRepository.hasPurchased(product.getId(), customer.getId(), OrderStatus.PAID))
                    .thenReturn(purchased);
            when(reviewRepository.existsByProductIdAndCustomerId(product.getId(), customer.getId()))
                    .thenReturn(reviewed);

            ReviewEligibility result = reviewService.eligibility(product.getId(), customer);

            assertEquals(purchased, result.purchased());
            assertEquals(reviewed, result.alreadyReviewed());
            assertEquals(canReview, result.canReview());
        }
    }
}