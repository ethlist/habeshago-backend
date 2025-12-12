package com.habeshago.user.dto;

import com.habeshago.user.User;

/**
 * DTO for user's contact method settings.
 */
public record ContactMethodsDto(
        // Telegram contact (from Telegram account, read-only)
        String username,
        Boolean contactTelegramEnabled,

        // Phone contact (user-entered)
        String phoneNumber,
        Boolean contactPhoneEnabled,

        // Computed
        Boolean hasAtLeastOneMethod
) {
    public static ContactMethodsDto from(User user) {
        return new ContactMethodsDto(
                user.getUsername(),
                user.getContactTelegramEnabled(),
                user.getPhoneNumber(),
                user.getContactPhoneEnabled(),
                user.hasAtLeastOneContactMethod()
        );
    }
}
