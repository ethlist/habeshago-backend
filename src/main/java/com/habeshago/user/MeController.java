package com.habeshago.user;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.common.BadRequestException;
import com.habeshago.user.dto.ContactMethodsDto;
import com.habeshago.user.dto.UpdateContactMethodsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for current user operations (/api/me/*).
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    /**
     * Get current user's profile.
     */
    @GetMapping
    public ResponseEntity<UserDto> getCurrentUser(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Get current user's contact method settings.
     */
    @GetMapping("/contact-methods")
    public ResponseEntity<ContactMethodsDto> getContactMethods(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(ContactMethodsDto.from(user));
    }

    /**
     * Update current user's contact method settings.
     * Users can enable/disable Telegram and Phone contact methods.
     * At least one must be enabled to create trips or send requests.
     */
    @PutMapping("/contact-methods")
    @Transactional
    public ResponseEntity<ContactMethodsDto> updateContactMethods(
            HttpServletRequest request,
            @Valid @RequestBody UpdateContactMethodsRequest body) {

        User user = requireCurrentUser(request);

        // Validate Telegram can only be enabled if user has a username
        if (Boolean.TRUE.equals(body.contactTelegramEnabled())) {
            if (user.getUsername() == null || user.getUsername().isBlank()) {
                throw new BadRequestException(
                        "Cannot enable Telegram contact without a Telegram username. " +
                        "Please link your Telegram account first.");
            }
        }

        // Validate phone can only be enabled if phone number is provided
        if (Boolean.TRUE.equals(body.contactPhoneEnabled())) {
            String phoneNumber = body.phoneNumber();
            if (phoneNumber == null || phoneNumber.isBlank()) {
                // Check if user already has a phone number
                if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                    throw new BadRequestException(
                            "Cannot enable phone contact without a phone number. " +
                            "Please provide a phone number.");
                }
            }
        }

        // Update contact preferences
        if (body.contactTelegramEnabled() != null) {
            user.setContactTelegramEnabled(body.contactTelegramEnabled());
        }

        if (body.contactPhoneEnabled() != null) {
            user.setContactPhoneEnabled(body.contactPhoneEnabled());
        }

        // Update phone number if provided
        if (body.phoneNumber() != null) {
            user.setPhoneNumber(body.phoneNumber().isBlank() ? null : body.phoneNumber().trim());
        }

        userRepository.save(user);

        return ResponseEntity.ok(ContactMethodsDto.from(user));
    }
}
