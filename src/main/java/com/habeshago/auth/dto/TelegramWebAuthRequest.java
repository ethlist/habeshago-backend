package com.habeshago.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for Telegram Login Widget authentication.
 * See: https://core.telegram.org/widgets/login
 *
 * The hash verification algorithm for the widget is:
 * 1. Build data-check-string (sorted key=value pairs, newline separated)
 * 2. secret_key = SHA256(bot_token)
 * 3. hash = HMAC-SHA256(data-check-string, secret_key)
 */
public record TelegramWebAuthRequest(
        @NotNull(message = "id is required")
        Long id,

        @JsonProperty("first_name")
        String firstName,

        @JsonProperty("last_name")
        String lastName,

        String username,

        @JsonProperty("photo_url")
        String photoUrl,

        @JsonProperty("auth_date")
        @NotNull(message = "auth_date is required")
        Long authDate,

        @NotNull(message = "hash is required")
        String hash
) {}
