package com.habeshago.user;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.auth.OAuthIdRecord;
import com.habeshago.auth.OAuthIdRecordRepository;
import com.habeshago.common.BadRequestException;
import com.habeshago.config.RetentionConfig;
import com.habeshago.request.ItemRequestRepository;
import com.habeshago.trip.TripRepository;
import com.habeshago.user.dto.ContactMethodsDto;
import com.habeshago.user.dto.UpdateContactMethodsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Controller for current user operations (/api/me/*).
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    private final UserRepository userRepository;
    private final OAuthIdRecordRepository oAuthIdRecordRepository;
    private final TripRepository tripRepository;
    private final ItemRequestRepository itemRequestRepository;
    private final RetentionConfig retentionConfig;

    public MeController(UserRepository userRepository,
                        OAuthIdRecordRepository oAuthIdRecordRepository,
                        TripRepository tripRepository,
                        ItemRequestRepository itemRequestRepository,
                        RetentionConfig retentionConfig) {
        this.userRepository = userRepository;
        this.oAuthIdRecordRepository = oAuthIdRecordRepository;
        this.tripRepository = tripRepository;
        this.itemRequestRepository = itemRequestRepository;
        this.retentionConfig = retentionConfig;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    // Note: GET /api/me is already in TripController

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

    /**
     * Delete the current user's account.
     * This performs a soft delete with immediate PII anonymization.
     * The account can be restored within the recovery period by logging in again.
     */
    @DeleteMapping("/account")
    @Transactional
    public ResponseEntity<AccountDeletionResponse> deleteAccount(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        Instant now = Instant.now();

        log.info("Account deletion requested by user {} (googleId={}, telegramId={})",
                user.getId(), user.getGoogleId(), user.getTelegramUserId());

        // 1. Record OAuth IDs in tracking table (for recreation prevention)
        OAuthIdRecord record = oAuthIdRecordRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    OAuthIdRecord newRecord = new OAuthIdRecord();
                    newRecord.setUserId(user.getId());
                    return newRecord;
                });

        // Store current OAuth IDs before clearing them
        if (user.getGoogleId() != null) {
            record.setGoogleId(user.getGoogleId());
        }
        if (user.getTelegramUserId() != null) {
            record.setTelegramUserId(user.getTelegramUserId());
        }
        record.setDeleted(true);
        record.setDeletedAt(now);
        record.setBlockedUntil(now.plus(retentionConfig.getOauthBlockDays(), ChronoUnit.DAYS));
        oAuthIdRecordRepository.save(record);

        // 2. Immediate PII anonymization
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setGoogleEmail(null);
        user.setEmail(null);
        user.setPhoneNumber(null);
        user.setUsername("deleted_" + user.getId());

        // 3. Clear OAuth links
        user.setGoogleId(null);
        user.setTelegramUserId(null);

        // 4. Soft delete
        user.setDeleted(true);
        user.setDeletedAt(now);
        user.setDeletionReason(DeletionReason.USER_REQUEST);

        // 5. Set retention period
        user.setRetentionUntil(now.plus(retentionConfig.getDataRetentionDays(), ChronoUnit.DAYS));

        // 6. Disable contact methods
        user.setContactTelegramEnabled(false);
        user.setContactPhoneEnabled(false);

        userRepository.save(user);

        // 7. Anonymize related data (trips and requests)
        int tripsAnonymized = tripRepository.anonymizeUserTrips(user.getId());
        int requestsAnonymized = itemRequestRepository.anonymizeUserRequests(user.getId());

        log.info("Account {} deleted: {} trips and {} requests anonymized",
                user.getId(), tripsAnonymized, requestsAnonymized);

        return ResponseEntity.ok(new AccountDeletionResponse(
                "Account deleted successfully",
                now,
                retentionConfig.getAccountRecoveryDays()
        ));
    }

    public record AccountDeletionResponse(
            String message,
            Instant deletedAt,
            int recoveryPeriodDays
    ) {}
}
