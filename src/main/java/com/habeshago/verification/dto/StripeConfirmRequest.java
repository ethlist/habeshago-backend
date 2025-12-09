package com.habeshago.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record StripeConfirmRequest(
        @NotBlank String sessionId
) {}
