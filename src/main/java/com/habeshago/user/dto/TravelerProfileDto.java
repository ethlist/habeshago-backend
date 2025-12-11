package com.habeshago.user.dto;

import com.habeshago.review.Review;
import com.habeshago.review.dto.ReviewDto;
import com.habeshago.user.User;

import java.time.Instant;
import java.util.List;

public record TravelerProfileDto(
        String userId,
        String firstName,
        String lastName,
        String username,
        Boolean verified,
        Instant verifiedAt,
        Double ratingAverage,
        Integer ratingCount,
        Integer completedTripsCount,
        Integer completedDeliveriesCount,
        Integer completionRate, // Percentage (0-100), null if no data
        Instant memberSince,
        List<ReviewDto> recentReviews
) {
    public static TravelerProfileDto from(User user, List<Review> recentReviews) {
        return new TravelerProfileDto(
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getVerified(),
                user.getVerifiedAt(),
                user.getRatingAverage(),
                user.getRatingCount(),
                user.getCompletedTripsCount(),
                user.getCompletedDeliveriesCount(),
                user.getCompletionRate(),
                user.getCreatedAt(),
                recentReviews.stream().map(ReviewDto::from).toList()
        );
    }
}
