package com.habeshago.trip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshago.common.BadRequestException;
import com.habeshago.common.ForbiddenException;
import com.habeshago.common.NotFoundException;
import com.habeshago.notification.NotificationOutbox;
import com.habeshago.notification.NotificationService;
import com.habeshago.notification.NotificationType;
import com.habeshago.request.ItemRequest;
import com.habeshago.request.RequestStatus;
import com.habeshago.trip.dto.TripCreateRequest;
import com.habeshago.trip.dto.TripDto;
import com.habeshago.trip.dto.TripUpdateRequest;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public TripService(TripRepository tripRepository, UserRepository userRepository,
                       NotificationService notificationService) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public TripDto createTrip(User currentUser, TripCreateRequest req) {
        // Validate arrival date is on or after departure date
        if (req.getArrivalDate() != null && req.getDepartureDate() != null
                && req.getArrivalDate().isBefore(req.getDepartureDate())) {
            throw new BadRequestException("Arrival date cannot be before departure date");
        }

        // Validate contact method and determine contact value
        ContactMethod contactMethod = req.getContactMethod();
        String contactValue = resolveContactValue(currentUser, contactMethod);

        Trip trip = new Trip();
        trip.setUser(currentUser);
        trip.setFromCity(req.getFromCity());
        trip.setFromCountry(req.getFromCountry());
        trip.setFromAirportCode(req.getFromAirportCode());
        trip.setToCity(req.getToCity());
        trip.setToCountry(req.getToCountry());
        trip.setToAirportCode(req.getToAirportCode());
        trip.setDepartureDate(req.getDepartureDate());
        trip.setArrivalDate(req.getArrivalDate());
        trip.setCapacityType(req.getCapacityType());
        trip.setMaxWeightKg(req.getMaxWeightKg());
        trip.setNotes(req.getNotes());
        trip.setStatus(TripStatus.OPEN);
        trip.setContactMethod(contactMethod);
        trip.setContactValue(contactValue);

        Trip saved = tripRepository.save(trip);
        return TripDto.from(saved);
    }

    /**
     * Validates the contact method choice and returns the contact value to store.
     * @throws BadRequestException if the contact method is not available for this user
     */
    private String resolveContactValue(User user, ContactMethod contactMethod) {
        if (contactMethod == ContactMethod.TELEGRAM) {
            // User must have Telegram linked
            if (user.getTelegramUserId() == null && user.getUsername() == null) {
                throw new BadRequestException("Telegram is not linked to your account");
            }
            // Use username if available, otherwise use @telegramUserId
            return user.getUsername() != null ? user.getUsername() : "@" + user.getTelegramUserId();
        } else if (contactMethod == ContactMethod.PHONE) {
            // User must have verified phone
            if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
                throw new BadRequestException("Phone number is not verified. Please verify your phone first.");
            }
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isEmpty()) {
                throw new BadRequestException("No phone number on file");
            }
            return user.getPhoneNumber();
        }
        throw new BadRequestException("Invalid contact method");
    }

    @Transactional(readOnly = true)
    public List<TripDto> getMyTrips(User currentUser) {
        return tripRepository.findByUserOrderByDepartureDateDesc(currentUser).stream()
                .map(TripDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDto getTrip(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        return TripDto.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripDto> searchTrips(String from, String to, String date, String capacityType) {
        // Parse date if provided
        LocalDate departureDate = null;
        if (date != null && !date.isEmpty()) {
            departureDate = LocalDate.parse(date);
        }

        // Parse capacityType if provided
        CapacityType capType = null;
        if (capacityType != null && !capacityType.isEmpty()) {
            capType = CapacityType.valueOf(capacityType);
        }

        List<Trip> trips = tripRepository.searchTrips(from, to, departureDate, TripStatus.OPEN.name());

        // capacityType filter at app level if provided
        final CapacityType finalCapType = capType;
        return trips.stream()
                .filter(t -> finalCapType == null || t.getCapacityType() == finalCapType)
                .map(TripDto::from)
                .toList();
    }

    @Transactional
    public TripDto cancelTrip(Long tripId, Long userId, String reason) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        // Only owner can cancel
        if (!trip.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the trip owner can cancel");
        }

        // Can only cancel OPEN or PARTIALLY_BOOKED trips
        if (trip.getStatus() != TripStatus.OPEN && trip.getStatus() != TripStatus.PARTIALLY_BOOKED) {
            throw new BadRequestException("Can only cancel open or partially booked trips");
        }

        // Check if there are any accepted requests - block cancellation if so
        long acceptedCount = trip.getRequests() != null
                ? trip.getRequests().stream()
                        .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                        .count()
                : 0;

        if (acceptedCount > 0) {
            throw new BadRequestException("Cannot cancel trip with accepted requests. Please contact the senders first.");
        }

        // Cancel all pending requests and notify senders
        if (trip.getRequests() != null) {
            for (ItemRequest request : trip.getRequests()) {
                if (request.getStatus() == RequestStatus.PENDING) {
                    request.setStatus(RequestStatus.CANCELLED_BY_TRAVELER);
                    notifyTripCancelled(request, trip, reason);
                }
            }
        }

        trip.setStatus(TripStatus.CANCELLED);
        trip.setCancellationReason(reason);
        tripRepository.save(trip);

        return TripDto.from(trip);
    }

    @Transactional
    public TripDto updateTrip(Long tripId, Long userId, TripUpdateRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        // Only owner can edit
        if (!trip.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the trip owner can edit");
        }

        // Can only edit OPEN or PARTIALLY_BOOKED trips
        if (trip.getStatus() != TripStatus.OPEN && trip.getStatus() != TripStatus.PARTIALLY_BOOKED) {
            throw new BadRequestException("Can only edit open or partially booked trips");
        }

        // Check if there are any accepted requests - block edit if so
        long acceptedCount = trip.getRequests() != null
                ? trip.getRequests().stream()
                        .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                        .count()
                : 0;

        if (acceptedCount > 0) {
            throw new BadRequestException("Cannot edit trip with accepted requests");
        }

        // Update only provided fields
        if (req.getFromCity() != null) {
            trip.setFromCity(req.getFromCity());
        }
        if (req.getFromCountry() != null) {
            trip.setFromCountry(req.getFromCountry());
        }
        if (req.getFromAirportCode() != null) {
            trip.setFromAirportCode(req.getFromAirportCode());
        }
        if (req.getToCity() != null) {
            trip.setToCity(req.getToCity());
        }
        if (req.getToCountry() != null) {
            trip.setToCountry(req.getToCountry());
        }
        if (req.getToAirportCode() != null) {
            trip.setToAirportCode(req.getToAirportCode());
        }
        if (req.getDepartureDate() != null) {
            trip.setDepartureDate(req.getDepartureDate());
        }
        if (req.getArrivalDate() != null) {
            trip.setArrivalDate(req.getArrivalDate());
        }
        if (req.getCapacityType() != null) {
            trip.setCapacityType(req.getCapacityType());
        }
        if (req.getMaxWeightKg() != null) {
            trip.setMaxWeightKg(req.getMaxWeightKg());
        }
        if (req.getNotes() != null) {
            trip.setNotes(req.getNotes());
        }
        if (req.getContactMethod() != null) {
            // Validate and resolve the new contact method
            String contactValue = resolveContactValue(trip.getUser(), req.getContactMethod());
            trip.setContactMethod(req.getContactMethod());
            trip.setContactValue(contactValue);
        }

        // Validate arrival date is on or after departure date
        if (trip.getArrivalDate() != null && trip.getDepartureDate() != null
                && trip.getArrivalDate().isBefore(trip.getDepartureDate())) {
            throw new BadRequestException("Arrival date cannot be before departure date");
        }

        Trip saved = tripRepository.save(trip);
        return TripDto.from(saved);
    }

    private void notifyTripCancelled(ItemRequest request, Trip trip, String reason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", NotificationType.TRIP_CANCELLED.name());
            payload.put("itemDescription", request.getDescription());
            payload.put("route", trip.getFromCity() + " → " + trip.getToCity());
            payload.put("departureDate", trip.getDepartureDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            payload.put("travelerFirstName", trip.getUser().getFirstName());
            payload.put("reason", reason != null ? reason : "No reason provided");

            NotificationOutbox outbox = new NotificationOutbox();
            outbox.setUser(request.getSenderUser());
            outbox.setType(NotificationType.TRIP_CANCELLED.name());
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            notificationService.enqueueNotification(outbox);

            log.info("Enqueued trip cancellation notification for sender {} on trip {}",
                    request.getSenderUser().getId(), trip.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload for trip cancellation", e);
        }
    }

    @Transactional
    public TripDto markAsCompleted(Long tripId, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        // Only owner can mark as completed
        if (!trip.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the trip owner can mark as completed");
        }

        // Can only complete non-cancelled trips
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new BadRequestException("Trip is already " + trip.getStatus().name().toLowerCase());
        }

        trip.setStatus(TripStatus.COMPLETED);
        tripRepository.save(trip);

        // Update user's completed trips count
        User user = trip.getUser();
        user.setCompletedTripsCount(user.getCompletedTripsCount() + 1);
        userRepository.save(user);

        return TripDto.from(trip);
    }
}
