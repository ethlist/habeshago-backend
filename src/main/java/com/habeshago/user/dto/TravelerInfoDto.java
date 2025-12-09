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
        Integer completedTripsCount
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
                user.getCompletedTripsCount()
        );
    }
}
