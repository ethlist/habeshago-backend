package com.habeshago.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for updating contact method preferences.
 */
public record UpdateContactMethodsRequest(
        Boolean contactTelegramEnabled,
        Boolean contactPhoneEnabled,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        String phoneNumber
) {}
