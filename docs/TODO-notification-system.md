# TODO: Enhanced Notification System

## Overview
Implement a comprehensive notification system that supports:
- In-app notifications (bell icon with dropdown)
- Email notifications
- SMS notifications (via phone number)
- User preferences for notification channels

## Current State
- Telegram push notifications working for Telegram users
- Web-only users receive NO notifications (silently skipped)
- Notifications stored in `notification_outbox` table but only for Telegram delivery

---

## Phase 1: In-App Notifications

### Backend Changes

#### 1. Create Notification Entity
```java
// src/main/java/com/habeshago/notification/Notification.java
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String type; // NEW_REQUEST, REQUEST_ACCEPTED, etc.

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "action_url")
    private String actionUrl; // e.g., /requests/123

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}
```

#### 2. Create Repository
```java
// src/main/java/com/habeshago/notification/NotificationRepository.java
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") Instant now);
}
```

#### 3. Create REST Endpoints
```java
// src/main/java/com/habeshago/notification/NotificationController.java
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @GetMapping
    public Page<NotificationDto> getNotifications(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal UserPrincipal user);

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user);

    @PatchMapping("/read-all")
    public void markAllAsRead(@AuthenticationPrincipal UserPrincipal user);
}
```

#### 4. Modify NotificationService
- When enqueuing a notification, ALSO create an in-app Notification entity
- Continue sending Telegram for Telegram users
- In-app notifications available for ALL users

### Frontend Changes

#### 1. Create API hooks
```typescript
// src/hooks/useNotifications.ts
export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get('/notifications'),
    refetchInterval: 30000, // Poll every 30 seconds
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => api.get('/notifications/unread-count'),
    refetchInterval: 30000,
    refetchOnWindowFocus: true,
  });
}
```

#### 2. Create NotificationBell component
```typescript
// src/components/NotificationBell.tsx
- Bell icon with badge showing unread count
- Dropdown with notification list
- Click notification -> navigate to actionUrl
- "Mark all as read" button
```

#### 3. Add to AppLayout header
- Replace removed notification badge with real NotificationBell component

### Database Migration
```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    action_url VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at TIMESTAMP,

    INDEX idx_notifications_user_created (user_id, created_at DESC),
    INDEX idx_notifications_user_unread (user_id, is_read)
);
```

---

## Phase 2: Email Notifications

### Backend Changes

#### 1. Add Email Service
```java
// src/main/java/com/habeshago/notification/EmailService.java
@Service
public class EmailService {
    private final JavaMailSender mailSender;

    public void sendNotificationEmail(User user, String subject, String htmlContent);
}
```

#### 2. Create Email Templates
- Use Thymeleaf or similar for HTML email templates
- Templates for each notification type:
  - `new-request.html`
  - `request-accepted.html`
  - `request-rejected.html`
  - `request-delivered.html`

#### 3. Configuration
```yaml
# application.yml
spring:
  mail:
    host: smtp.gmail.com  # or SendGrid, AWS SES, etc.
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

### Email Provider Options
- **SendGrid** - Good free tier, easy API
- **AWS SES** - Cheapest at scale
- **Mailgun** - Good for transactional emails
- **Gmail SMTP** - Free but limited (500/day)

---

## Phase 3: SMS Notifications

### Backend Changes

#### 1. Add SMS Service
```java
// src/main/java/com/habeshago/notification/SmsService.java
@Service
public class SmsService {
    // Use Twilio, AWS SNS, or Africa's Talking (for Ethiopian numbers)

    public void sendSms(String phoneNumber, String message);
}
```

#### 2. Configuration
```yaml
# application.yml
habeshago:
  sms:
    provider: twilio  # or africastalking
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    from-number: ${TWILIO_PHONE_NUMBER}
```

### SMS Provider Options
- **Twilio** - Global coverage, reliable
- **Africa's Talking** - Better for Ethiopian/African numbers, cheaper
- **AWS SNS** - Good if already using AWS

---

## Phase 4: User Notification Preferences

### Backend Changes

#### 1. Add Preferences to User or New Table
```java
// Option A: Add to User entity
@Column(name = "notify_email")
private boolean notifyByEmail = true;

@Column(name = "notify_sms")
private boolean notifyBySms = false;

@Column(name = "notify_in_app")
private boolean notifyInApp = true;

// Option B: Separate preferences table for granular control
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {
    @Id
    private Long userId;

    // Per-channel preferences
    private boolean emailEnabled = true;
    private boolean smsEnabled = false;
    private boolean inAppEnabled = true;

    // Per-type preferences (optional, for granular control)
    private boolean emailOnNewRequest = true;
    private boolean emailOnRequestAccepted = true;
    // ... etc
}
```

#### 2. API Endpoints
```java
@GetMapping("/api/users/me/notification-preferences")
public NotificationPreferencesDto getPreferences();

@PatchMapping("/api/users/me/notification-preferences")
public NotificationPreferencesDto updatePreferences(@RequestBody UpdatePreferencesRequest request);
```

### Frontend Changes

#### 1. Settings Screen
- Add notification preferences section to ProfileScreen or create dedicated SettingsScreen
- Toggle switches for each channel (Email, SMS, In-App)
- Optionally: per-notification-type toggles

```typescript
// Example UI
Notification Settings
├── In-App Notifications    [✓]
├── Email Notifications     [✓]
│   └── Email: user@example.com
├── SMS Notifications       [ ]
│   └── Phone: +1234567890 (not set)
└── Telegram (via Mini App) [Auto-enabled]
```

---

## Phase 5: Unified Notification Dispatcher

### Modify NotificationService
```java
@Service
public class NotificationService {

    public void sendNotification(User user, NotificationType type, Map<String, Object> payload) {
        // 1. Always create in-app notification
        createInAppNotification(user, type, payload);

        // 2. Check user preferences and send to enabled channels
        NotificationPreferences prefs = getPreferences(user);

        if (user.getTelegramUserId() != null) {
            // Telegram users get Telegram notifications (existing behavior)
            enqueueTelegramNotification(user, type, payload);
        }

        if (prefs.isEmailEnabled() && user.getEmail() != null) {
            enqueueEmailNotification(user, type, payload);
        }

        if (prefs.isSmsEnabled() && user.getPhoneNumber() != null) {
            enqueueSmsNotification(user, type, payload);
        }
    }
}
```

---

## Implementation Order

1. **Phase 1: In-App** - Most impact, enables notifications for all users
2. **Phase 4: Preferences** - Add UI before adding more channels
3. **Phase 2: Email** - Common expectation, good fallback
4. **Phase 3: SMS** - Optional, adds cost per message

---

## Cost Considerations

| Channel | Cost | Notes |
|---------|------|-------|
| In-App | Free | Just database storage |
| Telegram | Free | Already implemented |
| Email (SendGrid) | Free up to 100/day | $15/mo for 50k emails |
| Email (AWS SES) | $0.10 per 1000 | Cheapest at scale |
| SMS (Twilio) | ~$0.0075/SMS | Varies by country |
| SMS (Africa's Talking) | ~$0.02/SMS to Ethiopia | Better regional coverage |

---

## Security Considerations

- Rate limit notification sending to prevent spam
- Validate phone numbers before enabling SMS
- Email verification before enabling email notifications
- Don't expose sensitive data in SMS (character limit, less secure)
- Allow users to unsubscribe from each channel

---

## Testing Checklist

- [ ] In-app notifications appear in bell dropdown
- [ ] Unread count updates correctly
- [ ] Mark as read works (single and all)
- [ ] Email notifications delivered correctly
- [ ] Email unsubscribe link works
- [ ] SMS delivered to valid phone numbers
- [ ] User preferences are respected
- [ ] Telegram notifications still work for TG users
- [ ] Web users now receive in-app + email/SMS based on preferences
