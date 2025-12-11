package com.habeshago.verification.dto;

public record VerificationStatusResponse(
        String status,
        boolean phoneVerified,
        boolean idVerified,
        String rejectionReason,
        int verificationAttempts
) {}
