package com.habeshago.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for Google Sign-In authentication.
 * The idToken is obtained from Google Sign-In on the frontend.
 */
public record GoogleAuthRequest(
        @NotBlank(message = "Google ID token is required")
        String idToken
) {}
