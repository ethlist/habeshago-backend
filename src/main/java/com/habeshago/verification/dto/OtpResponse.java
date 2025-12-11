package com.habeshago.verification.dto;

public record OtpResponse(
        boolean success,
        String message,
        Long cooldownSeconds,
        // Only populated in dev mode for testing
        String devOtp
) {
    public static OtpResponse success(String message) {
        return new OtpResponse(true, message, null, null);
    }

    public static OtpResponse successWithDevOtp(String message, String devOtp) {
        return new OtpResponse(true, message, null, devOtp);
    }

    public static OtpResponse cooldown(long seconds) {
        return new OtpResponse(false, "Please wait before requesting a new code", seconds, null);
    }

    public static OtpResponse error(String message) {
        return new OtpResponse(false, message, null, null);
    }
}
