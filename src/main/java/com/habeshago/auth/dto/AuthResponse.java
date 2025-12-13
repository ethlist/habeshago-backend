package com.habeshago.auth.dto;

import com.habeshago.user.UserDto;

public record AuthResponse(
        String token,
        UserDto user,
        Boolean restored // true if account was restored from deletion
) {
    // Constructor without restored flag (defaults to false)
    public AuthResponse(String token, UserDto user) {
        this(token, user, false);
    }
}
