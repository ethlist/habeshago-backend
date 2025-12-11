package com.habeshago.user.dto;

import com.habeshago.user.User;

public record TravelerInfoDto(
        String id,
        String firstName,
        String lastName,
        String username,
        Boolean verified,
        Double ratingAverage,
        Integer ratingCount,
        Integer completedTripsCount,
        Integer completedDeliveriesCount,
        Integer completionRate // Percentage (0-100), null if no data
) {
    public static TravelerInfoDto from(User user) {
        return new TravelerInfoDto(
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getVerified(),
                user.getRatingAverage(),
                user.getRatingCount(),
                user.getCompletedTripsCount(),
                user.getCompletedDeliveriesCount(),
                user.getCompletionRate()
        );
    }
}
