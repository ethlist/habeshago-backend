package com.habeshago.user.dto;

import com.habeshago.user.User;

public record SenderInfoDto(
        String id,
        String firstName,
        String lastName,
        String username
) {
    public static SenderInfoDto from(User user) {
        return new SenderInfoDto(
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername()
        );
    }
}
