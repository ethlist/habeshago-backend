package com.habeshago.auth;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks OAuth IDs permanently to prevent account recreation abuse.
 * This table survives user hard-deletion.
 */
@Entity
@Table(name = "oauth_id_records")
public class OAuthIdRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "telegram_user_id", unique = true)
    private Long telegramUserId;

    // NOT a foreign key - user may be deleted
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "permanently_blocked", nullable = false)
    private Boolean permanentlyBlocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public Instant getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(Instant blockedUntil) { this.blockedUntil = blockedUntil; }

    public Boolean getPermanentlyBlocked() { return permanentlyBlocked; }
    public void setPermanentlyBlocked(Boolean permanentlyBlocked) { this.permanentlyBlocked = permanentlyBlocked; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Check if this OAuth ID is currently blocked from account creation.
     */
    public boolean isBlocked() {
        if (Boolean.TRUE.equals(permanentlyBlocked)) {
            return true;
        }
        if (blockedUntil != null && blockedUntil.isAfter(Instant.now())) {
            return true;
        }
        return false;
    }
}
