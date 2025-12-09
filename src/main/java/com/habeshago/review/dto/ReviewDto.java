package com.habeshago.review.dto;

import com.habeshago.review.Review;
import com.habeshago.user.User;

import java.time.Instant;

public record ReviewDto(
        String id,
        String requestId,
        String reviewerUserId,
        String revieweeUserId,
        Integer rating,
        String comment,
        Instant createdAt,
        ReviewerDto reviewer
) {
    public static ReviewDto from(Review review) {
        User reviewer = review.getReviewer();
        User reviewee = review.getReviewedTraveler();
        return new ReviewDto(
                review.getId().toString(),
                review.getItemRequest().getId().toString(),
                reviewer.getId().toString(),
                reviewee.getId().toString(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                ReviewerDto.from(reviewer)
        );
    }

    public record ReviewerDto(
            String id,
            String firstName,
            String lastName,
            String username
    ) {
        public static ReviewerDto from(User user) {
            return new ReviewerDto(
                    user.getId().toString(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getUsername()
            );
        }
    }
}
