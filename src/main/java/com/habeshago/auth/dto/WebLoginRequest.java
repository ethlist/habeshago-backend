package com.habeshago.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record WebLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
