package com.habeshago.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WebRegisterRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
        )
        String password,
        @NotBlank String firstName,
        String lastName
) {}
