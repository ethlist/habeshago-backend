package com.habeshago.user;

import com.habeshago.verification.IDType;
import com.habeshago.verification.VerificationStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "telegram_user_id"),
        @UniqueConstraint(columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Telegram users have this set, web users have it null
    @Column(name = "telegram_user_id", unique = true)
    private Long telegramUserId;

    // Web authentication fields
    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "username")
    private String username;

    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = "en";

    // Profile edit tracking - prevents Telegram from overwriting user-edited profile
    @Column(name = "profile_edited_by_user", nullable = false)
    private Boolean profileEditedByUser = false;

    // Legacy verification fields (kept for backwards compatibility)
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    // Enhanced verification fields
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.NONE;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "phone_verified", nullable = false)
    private Boolean phoneVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", length = 20)
    private IDType idType;

    @Column(name = "id_photo_url", length = 500)
    private String idPhotoUrl;

    @Column(name = "selfie_url", length = 500)
    private String selfieUrl;

    @Column(name = "verification_rejection_reason", length = 500)
    private String verificationRejectionReason;

    @Column(name = "verification_attempts", nullable = false)
    private Integer verificationAttempts = 0;

    @Column(name = "verification_submitted_at")
    private Instant verificationSubmittedAt;

    @Column(name = "verification_reviewed_at")
    private Instant verificationReviewedAt;

    // Reputation fields (denormalized for fast reads)
    @Column(name = "rating_average")
    private Double ratingAverage;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "completed_trips_count", nullable = false)
    private Integer completedTripsCount = 0;

    @Column(name = "completed_deliveries_count", nullable = false)
    private Integer completedDeliveriesCount = 0;

    // Track accepted requests to calculate completion rate
    @Column(name = "accepted_requests_count", nullable = false)
    private Integer acceptedRequestsCount = 0;

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

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public Boolean getProfileEditedByUser() { return profileEditedByUser; }
    public void setProfileEditedByUser(Boolean profileEditedByUser) { this.profileEditedByUser = profileEditedByUser; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public IDType getIdType() { return idType; }
    public void setIdType(IDType idType) { this.idType = idType; }

    public String getIdPhotoUrl() { return idPhotoUrl; }
    public void setIdPhotoUrl(String idPhotoUrl) { this.idPhotoUrl = idPhotoUrl; }

    public String getSelfieUrl() { return selfieUrl; }
    public void setSelfieUrl(String selfieUrl) { this.selfieUrl = selfieUrl; }

    public String getVerificationRejectionReason() { return verificationRejectionReason; }
    public void setVerificationRejectionReason(String verificationRejectionReason) { this.verificationRejectionReason = verificationRejectionReason; }

    public Integer getVerificationAttempts() { return verificationAttempts; }
    public void setVerificationAttempts(Integer verificationAttempts) { this.verificationAttempts = verificationAttempts; }

    public Instant getVerificationSubmittedAt() { return verificationSubmittedAt; }
    public void setVerificationSubmittedAt(Instant verificationSubmittedAt) { this.verificationSubmittedAt = verificationSubmittedAt; }

    public Instant getVerificationReviewedAt() { return verificationReviewedAt; }
    public void setVerificationReviewedAt(Instant verificationReviewedAt) { this.verificationReviewedAt = verificationReviewedAt; }

    public Double getRatingAverage() { return ratingAverage; }
    public void setRatingAverage(Double ratingAverage) { this.ratingAverage = ratingAverage; }

    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }

    public Integer getCompletedTripsCount() { return completedTripsCount; }
    public void setCompletedTripsCount(Integer completedTripsCount) { this.completedTripsCount = completedTripsCount; }

    public Integer getCompletedDeliveriesCount() { return completedDeliveriesCount; }
    public void setCompletedDeliveriesCount(Integer completedDeliveriesCount) { this.completedDeliveriesCount = completedDeliveriesCount; }

    public Integer getAcceptedRequestsCount() { return acceptedRequestsCount; }
    public void setAcceptedRequestsCount(Integer acceptedRequestsCount) { this.acceptedRequestsCount = acceptedRequestsCount; }

    /**
     * Calculate completion rate as percentage.
     * Returns null if no accepted requests yet (shows as "New traveler").
     */
    public Integer getCompletionRate() {
        if (acceptedRequestsCount == null || acceptedRequestsCount == 0) {
            return null; // No data yet
        }
        int delivered = completedDeliveriesCount != null ? completedDeliveriesCount : 0;
        return (int) Math.round((delivered * 100.0) / acceptedRequestsCount);
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
