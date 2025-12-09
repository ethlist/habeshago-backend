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
        Instant createdAt,
        Instant updatedAt,
        // Traveler info with reputation
        TravelerInfoDto traveler,
        // Request counts
        Integer requestCount,
        Integer pendingRequestCount
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
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                includeTraveler && trip.getUser() != null ? TravelerInfoDto.from(trip.getUser()) : null,
                totalRequests,
                (int) pendingRequests
        );
    }
}
