package com.habeshago.trip;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.trip.dto.TripCreateRequest;
import com.habeshago.trip.dto.TripDto;
import com.habeshago.user.User;
import com.habeshago.user.UserDto;
import com.habeshago.user.LanguageUpdateRequest;
import com.habeshago.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Trip and simple user profile endpoints under /api.
 */
@RestController
@RequestMapping("/api")
public class TripController {

    private final TripService tripService;
    private final UserRepository userRepository;

    public TripController(TripService tripService, UserRepository userRepository) {
        this.tripService = tripService;
        this.userRepository = userRepository;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    // ----- User profile endpoints -----

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(UserDto.from(user));
    }

    @PutMapping("/me/language")
    public ResponseEntity<UserDto> updateLanguage(
            HttpServletRequest request,
            @Valid @RequestBody LanguageUpdateRequest body) {
        User user = requireCurrentUser(request);
        user.setPreferredLanguage(body.getLanguage());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(UserDto.from(saved));
    }

    // ----- Trip endpoints -----

    @PostMapping("/trips")
    public ResponseEntity<TripDto> createTrip(
            HttpServletRequest request,
            @Valid @RequestBody TripCreateRequest body) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(tripService.createTrip(user, body));
    }

    @GetMapping("/trips/my")
    public ResponseEntity<List<TripDto>> getMyTrips(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(tripService.getMyTrips(user));
    }

    @GetMapping("/trips/{id}")
    public ResponseEntity<TripDto> getTrip(HttpServletRequest request, @PathVariable Long id) {
        // Require authentication to protect traveler data from scrapers
        requireCurrentUser(request);
        return ResponseEntity.ok(tripService.getTrip(id));
    }

    @GetMapping("/trips/search")
    public ResponseEntity<List<TripDto>> searchTrips(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String capacityType) {
        return ResponseEntity.ok(tripService.searchTrips(from, to, date, capacityType));
    }

    @PostMapping("/trips/{id}/cancel")
    public ResponseEntity<TripDto> cancelTrip(
            HttpServletRequest request,
            @PathVariable Long id) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(tripService.cancelTrip(id, user.getId()));
    }

    @PostMapping("/trips/{id}/complete")
    public ResponseEntity<TripDto> completeTrip(
            HttpServletRequest request,
            @PathVariable Long id) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(tripService.markAsCompleted(id, user.getId()));
    }
}
