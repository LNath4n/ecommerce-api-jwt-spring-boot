package com.ecommerce.FerreViky.dto.dtos.review;

import com.ecommerce.FerreViky.models.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ReviewDtos {

    private ReviewDtos() {}

    public record ReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 1000) String comment) {}

    public record ReviewResponse(
            Long id,
            Long productId,
            Integer rating,
            String comment,
            LocalDateTime createdAt) {

        public static ReviewResponse from(Review r) {
            // getProduct().getId() no dispara carga del producto (es un proxy LAZY)
            return new ReviewResponse(r.getId(), r.getProduct().getId(),
                    r.getRating(), r.getComment(), r.getCreatedAt());
        }
    }

    public record ReviewSummary(Long productId, double average, long count) {}

    public record ReviewEligibility(boolean purchased, boolean alreadyReviewed) {
        public boolean canReview() {
            return purchased && !alreadyReviewed;
        }
    }
}