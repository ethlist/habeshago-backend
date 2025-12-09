package com.habeshago.notification.dto;

import com.habeshago.notification.Notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String message,
        String actionUrl,
        boolean isRead,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionUrl(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
