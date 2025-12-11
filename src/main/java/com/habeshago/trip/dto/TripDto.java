package com.habeshago.trip.dto;

import com.habeshago.request.RequestStatus;
import com.habeshago.trip.Trip;
import com.habeshago.user.dto.TravelerInfoDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TripDto(
        String id,
        String userId,
        String fromCity,
        String fromCountry,
        String fromAirportCode,
        String toCity,
        String toCountry,
        String toAirportCode,
        LocalDate departureDate,
        LocalDate arrivalDate,
        String capacityType,
        BigDecimal maxWeightKg,
        String notes,
        String status,
        String cancellationReason,
        // Contact method info
        String contactMethod,
        String contactValue,
        Instant createdAt,
        Instant updatedAt,
        // Traveler info with reputation
        TravelerInfoDto traveler,
        // Request counts
        Integer requestCount,
        Integer pendingRequestCount,
        Integer acceptedRequestCount,
        // Computed flag for edit/cancel eligibility
        Boolean canEdit
) {
    public static TripDto from(Trip trip) {
        return from(trip, true);
    }

    public static TripDto from(Trip trip, boolean includeTraveler) {
        int totalRequests = trip.getRequests() != null ? trip.getRequests().size() : 0;
        long pendingRequests = trip.getRequests() != null
                ? trip.getRequests().stream()
                        .filter(r -> r.getStatus() == RequestStatus.PENDING)
                        .count()
                : 0;
        long acceptedRequests = trip.getRequests() != null
                ? trip.getRequests().stream()
                        .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                        .count()
                : 0;

        // Trip can be edited/cancelled only if there are no accepted requests
        // and trip is in OPEN or PARTIALLY_BOOKED status
        boolean canEdit = acceptedRequests == 0 &&
                (trip.getStatus() == com.habeshago.trip.TripStatus.OPEN ||
                 trip.getStatus() == com.habeshago.trip.TripStatus.PARTIALLY_BOOKED);

        return new TripDto(
                trip.getId().toString(),
                trip.getUser() != null ? trip.getUser().getId().toString() : null,
                trip.getFromCity(),
                trip.getFromCountry(),
                trip.getFromAirportCode(),
                trip.getToCity(),
                trip.getToCountry(),
                trip.getToAirportCode(),
                trip.getDepartureDate(),
                trip.getArrivalDate(),
                trip.getCapacityType().name(),
                trip.getMaxWeightKg(),
                trip.getNotes(),
                trip.getStatus().name(),
                trip.getCancellationReason(),
                trip.getContactMethod() != null ? trip.getContactMethod().name() : null,
                trip.getContactValue(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                includeTraveler && trip.getUser() != null ? TravelerInfoDto.from(trip.getUser()) : null,
                totalRequests,
                (int) pendingRequests,
                (int) acceptedRequests,
                canEdit
        );
    }

    /**
     * Creates a new TripDto with contact information masked.
     * Used for unauthenticated users viewing trip details.
     */
    public TripDto withMaskedContact() {
        return new TripDto(
                id,
                userId,
                fromCity,
                fromCountry,
                fromAirportCode,
                toCity,
                toCountry,
                toAirportCode,
                departureDate,
                arrivalDate,
                capacityType,
                maxWeightKg,
                notes,
                status,
                cancellationReason,
                null,  // mask contactMethod
                null,  // mask contactValue
                createdAt,
                updatedAt,
                traveler,
                requestCount,
                pendingRequestCount,
                acceptedRequestCount,
                canEdit
        );
    }
}
