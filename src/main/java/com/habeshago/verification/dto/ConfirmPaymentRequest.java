package com.habeshago.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPaymentRequest(
        @NotBlank(message = "Payment ID is required")
        String paymentId
) {
}
