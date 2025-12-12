package com.habeshago.request;

import com.habeshago.trip.ContactMethod;
import com.habeshago.trip.Trip;
import com.habeshago.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "item_requests", indexes = {
        @Index(name = "idx_request_trip", columnList = "trip_id"),
        @Index(name = "idx_request_sender", columnList = "sender_user_id")
})
public class ItemRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User senderUser;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "special_instructions", length = 2000)
    private String specialInstructions;

    @Column(name = "pickup_photo_url", length = 500)
    private String pickupPhotoUrl;

    @Column(name = "delivery_photo_url", length = 500)
    private String deliveryPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    // Legacy single contact method fields (kept for backward compatibility)
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_contact_method", length = 20)
    private ContactMethod senderContactMethod;

    @Column(name = "sender_contact_value", length = 50)
    private String senderContactValue;

    // New multiple contact method fields
    @Column(name = "sender_contact_telegram", length = 50)
    private String senderContactTelegram;

    @Column(name = "sender_contact_phone", length = 20)
    private String senderContactPhone;

    // Track when contact was revealed (set when request is accepted)
    @Column(name = "contact_revealed_at")
    private Instant contactRevealedAt;

    @Column(name = "paid", nullable = false)
    private Boolean paid = false;

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

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public User getSenderUser() { return senderUser; }
    public void setSenderUser(User senderUser) { this.senderUser = senderUser; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public String getPickupPhotoUrl() { return pickupPhotoUrl; }
    public void setPickupPhotoUrl(String pickupPhotoUrl) { this.pickupPhotoUrl = pickupPhotoUrl; }

    public String getDeliveryPhotoUrl() { return deliveryPhotoUrl; }
    public void setDeliveryPhotoUrl(String deliveryPhotoUrl) { this.deliveryPhotoUrl = deliveryPhotoUrl; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }

    public ContactMethod getSenderContactMethod() { return senderContactMethod; }
    public void setSenderContactMethod(ContactMethod senderContactMethod) { this.senderContactMethod = senderContactMethod; }

    public String getSenderContactValue() { return senderContactValue; }
    public void setSenderContactValue(String senderContactValue) { this.senderContactValue = senderContactValue; }

    public String getSenderContactTelegram() { return senderContactTelegram; }
    public void setSenderContactTelegram(String senderContactTelegram) { this.senderContactTelegram = senderContactTelegram; }

    public String getSenderContactPhone() { return senderContactPhone; }
    public void setSenderContactPhone(String senderContactPhone) { this.senderContactPhone = senderContactPhone; }

    public Instant getContactRevealedAt() { return contactRevealedAt; }
    public void setContactRevealedAt(Instant contactRevealedAt) { this.contactRevealedAt = contactRevealedAt; }

    /**
     * Check if request has at least one sender contact method set.
     */
    public boolean hasAtLeastOneSenderContactMethod() {
        boolean hasTelegram = senderContactTelegram != null && !senderContactTelegram.isBlank();
        boolean hasPhone = senderContactPhone != null && !senderContactPhone.isBlank();
        return hasTelegram || hasPhone;
    }

    /**
     * Check if contact info should be revealed based on request status.
     */
    public boolean isContactRevealed() {
        return status == RequestStatus.ACCEPTED || contactRevealedAt != null;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
