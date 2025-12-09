package com.habeshago.request.dto;

import com.habeshago.request.ItemRequest;
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
                ir.getCreatedAt(),
                ir.getUpdatedAt(),
                includeTrip && ir.getTrip() != null ? TripDto.from(ir.getTrip()) : null,
                includeSender && ir.getSenderUser() != null ? SenderInfoDto.from(ir.getSenderUser()) : null
        );
    }
}
