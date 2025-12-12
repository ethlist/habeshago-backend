package com.habeshago.user;

import java.time.Instant;

public record UserDto(
        String id,
        Long telegramUserId,
        String googleId,
        String googleEmail,
        String firstName,
        String lastName,
        String username,
        String preferredLanguage,
        Boolean verified,
        Instant verifiedAt,
        Double ratingAverage,
        Integer ratingCount,
        Integer completedTripsCount,
        Integer completedDeliveriesCount,
        Integer acceptedRequestsCount,
        Integer completionRate, // Percentage (0-100), null if no data
        // Enhanced verification fields
        String verificationStatus,
        Boolean phoneVerified,
        String idType,
        String verificationRejectionReason,
        Integer verificationAttempts,
        // Identity verification (OAuth provider)
        Boolean identityVerified,
        String identityProvider,
        // Contact method preferences
        String phoneNumber,
        Boolean contactTelegramEnabled,
        Boolean contactPhoneEnabled,
        Boolean hasAtLeastOneContactMethod,
        // Trust system
        Integer trustScore
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId().toString(),
                user.getTelegramUserId(),
                user.getGoogleId(),
                user.getGoogleEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getPreferredLanguage(),
                user.getVerified(),
                user.getVerifiedAt(),
                user.getRatingAverage(),
                user.getRatingCount(),
                user.getCompletedTripsCount(),
                user.getCompletedDeliveriesCount(),
                user.getAcceptedRequestsCount(),
                user.getCompletionRate(),
                user.getVerificationStatus() != null ? user.getVerificationStatus().name() : "NONE",
                user.getPhoneVerified(),
                user.getIdType() != null ? user.getIdType().name() : null,
                user.getVerificationRejectionReason(),
                user.getVerificationAttempts(),
                user.getIdentityVerified(),
                user.getIdentityProvider(),
                user.getPhoneNumber(),
                user.getContactTelegramEnabled(),
                user.getContactPhoneEnabled(),
                user.hasAtLeastOneContactMethod(),
                user.getTrustScore()
        );
    }
}
