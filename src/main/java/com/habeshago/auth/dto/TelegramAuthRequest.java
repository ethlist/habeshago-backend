package com.habeshago.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TelegramAuthRequest(
        @NotBlank(message = "initData is required")
        String initData
) {}
