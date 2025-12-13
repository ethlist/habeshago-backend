package com.habeshago.request.dto;

import com.habeshago.request.ItemRequest;
import com.habeshago.request.RequestStatus;
import com.habeshago.trip.dto.TripDto;
import com.habeshago.user.dto.SenderInfoDto;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemRequestDto(
        String id,
        String tripId,
        String senderUserId,
        String description,
        BigDecimal weightKg,
        String specialInstructions,
        String pickupPhotoUrl,
        String deliveryPhotoUrl,
        String status,
        Boolean paid,
        // Legacy contact method info (for backward compatibility)
        String senderContactMethod,
        String senderContactValue,
        // New multiple contact method fields
        String senderContactTelegram,
        String senderContactPhone,
        // Track when contact was revealed
        Instant contactRevealedAt,
        Instant createdAt,
        Instant updatedAt,
        // Embedded objects
        TripDto trip,
        SenderInfoDto sender
) {
    public static ItemRequestDto from(ItemRequest ir) {
        return from(ir, false, true);
    }

    public static ItemRequestDto from(ItemRequest ir, boolean includeTrip, boolean includeSender) {
        // Check if contact should be revealed (request is accepted or delivered)
        boolean shouldRevealContact = ir.getStatus() == RequestStatus.ACCEPTED ||
                ir.getStatus() == RequestStatus.DELIVERED ||
                ir.getContactRevealedAt() != null;

        return new ItemRequestDto(
                ir.getId().toString(),
                ir.getTrip() != null ? ir.getTrip().getId().toString() : null,
                ir.getSenderUser() != null ? ir.getSenderUser().getId().toString() : null,
                ir.getDescription(),
                ir.getWeightKg(),
                ir.getSpecialInstructions(),
                ir.getPickupPhotoUrl(),
                ir.getDeliveryPhotoUrl(),
                ir.getStatus().name(),
                ir.getPaid(),
                // Only reveal contact info if request is accepted/delivered
                shouldRevealContact && ir.getSenderContactMethod() != null ? ir.getSenderContactMethod().name() : null,
                shouldRevealContact ? ir.getSenderContactValue() : null,
                shouldRevealContact ? ir.getSenderContactTelegram() : null,
                shouldRevealContact ? ir.getSenderContactPhone() : null,
                ir.getContactRevealedAt(),
                ir.getCreatedAt(),
                ir.getUpdatedAt(),
                includeTrip && ir.getTrip() != null ? TripDto.from(ir.getTrip()) : null,
                includeSender && ir.getSenderUser() != null ? SenderInfoDto.from(ir.getSenderUser()) : null
        );
    }

    /**
     * Creates a new ItemRequestDto with sender contact information masked.
     * Used when contact should be hidden (e.g., request not yet accepted).
     */
    public ItemRequestDto withMaskedSenderContact() {
        return new ItemRequestDto(
                id,
                tripId,
                senderUserId,
                description,
                weightKg,
                specialInstructions,
                pickupPhotoUrl,
                deliveryPhotoUrl,
                status,
                paid,
                null,  // mask senderContactMethod
                null,  // mask senderContactValue
                null,  // mask senderContactTelegram
                null,  // mask senderContactPhone
                contactRevealedAt,
                createdAt,
                updatedAt,
                trip,
                sender
        );
    }
}
