package com.habeshago.trip;

import com.habeshago.common.BadRequestException;
import com.habeshago.common.ForbiddenException;
import com.habeshago.common.NotFoundException;
import com.habeshago.trip.dto.TripCreateRequest;
import com.habeshago.trip.dto.TripDto;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public TripService(TripRepository tripRepository, UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TripDto createTrip(User currentUser, TripCreateRequest req) {
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

        Trip saved = tripRepository.save(trip);
        return TripDto.from(saved);
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

        List<Trip> trips = tripRepository.searchTrips(from, to, departureDate, TripStatus.OPEN);

        // capacityType filter at app level if provided
        final CapacityType finalCapType = capType;
        return trips.stream()
                .filter(t -> finalCapType == null || t.getCapacityType() == finalCapType)
                .map(TripDto::from)
                .toList();
    }

    @Transactional
    public TripDto cancelTrip(Long tripId, Long userId) {
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

        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);

        return TripDto.from(trip);
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
