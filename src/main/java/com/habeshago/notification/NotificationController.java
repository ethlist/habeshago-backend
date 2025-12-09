package com.habeshago.notification;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.common.NotFoundException;
import com.habeshago.notification.dto.NotificationDto;
import com.habeshago.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    /**
     * Get notifications for the current user
     * @param page Page number (0-indexed)
     * @param size Page size (default 20, max 50)
     */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = requireCurrentUser(request);

        // Limit page size to prevent abuse
        int safeSize = Math.min(size, 50);

        List<NotificationDto> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, safeSize))
                .stream()
                .map(NotificationDto::from)
                .toList();

        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notification count for the current user
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        long count = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark a single notification as read
     */
    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<NotificationDto> markAsRead(
            HttpServletRequest request,
            @PathVariable Long id) {
        User user = requireCurrentUser(request);

        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return ResponseEntity.ok(NotificationDto.from(notification));
    }

    /**
     * Mark all notifications as read for the current user
     */
    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllAsRead(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        int updated = notificationRepository.markAllAsRead(user.getId(), Instant.now());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
