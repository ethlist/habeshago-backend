package com.habeshago.auth.dto;

import com.habeshago.user.UserDto;

public record AuthResponse(
        String token,
        UserDto user
) {}
