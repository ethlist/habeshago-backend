package com.habeshago.verification;

import com.habeshago.common.BadRequestException;
import com.habeshago.telegram.TelegramClient;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic", "image/heif"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private static final Set<String> REJECTION_REASONS = Set.of(
            "ID photo is blurry or unreadable",
            "Selfie doesn't clearly show your face",
            "Face in selfie doesn't match ID photo",
            "ID appears to be expired",
            "Name on ID doesn't match account name"
    );

    private final UserRepository userRepository;
    private final SmsService smsService;
    private final StorageService storageService;
    private final TelegramClient telegramClient;
    private final Long adminTelegramId;

    // Track used payment IDs to prevent replay attacks
    private final ConcurrentHashMap<String, Long> usedPaymentIds = new ConcurrentHashMap<>();

    @Value("${habeshago.telegram.bot-token:REPLACE_ME}")
    private String telegramBotToken;

    @Value("${habeshago.verification.price:500}")
    private int verificationPriceStars;

    public VerificationService(
            UserRepository userRepository,
            SmsService smsService,
            StorageService storageService,
            TelegramClient telegramClient,
            @Value("${habeshago.admin.telegram-id:}") String adminTelegramIdStr) {
        this.userRepository = userRepository;
        this.smsService = smsService;
        this.storageService = storageService;
        this.telegramClient = telegramClient;
        this.adminTelegramId = adminTelegramIdStr != null && !adminTelegramIdStr.isBlank()
                ? Long.parseLong(adminTelegramIdStr) : null;
    }

    // ==================== Phone Verification ====================

    /**
     * Send OTP to the given phone number.
     */
    @Transactional
    public String sendOtp(User user, String phoneNumber) {
        user.setPhoneNumber(phoneNumber);
        if (user.getVerificationStatus() == null || user.getVerificationStatus() == VerificationStatus.NONE) {
            user.setVerificationStatus(VerificationStatus.PENDING_PHONE);
        }
        userRepository.save(user);

        return smsService.sendOtp(phoneNumber);
    }

    /**
     * Verify the OTP for the user.
     */
    @Transactional
    public boolean verifyOtp(User user, String phoneNumber, String otp) {
        if (!phoneNumber.equals(user.getPhoneNumber())) {
            throw new BadRequestException("Phone number doesn't match");
        }

        boolean verified = smsService.verifyOtp(phoneNumber, otp);
        if (verified) {
            user.setPhoneVerified(true);
            userRepository.save(user);
        }
        return verified;
    }

    /**
     * Get cooldown remaining for OTP resend.
     */
    public long getOtpCooldown(String phoneNumber) {
        return smsService.getCooldownRemaining(phoneNumber);
    }

    // ==================== ID Verification ====================

    /**
     * Submit ID verification documents.
     */
    @Transactional
    public void submitIdVerification(User user, IDType idType, MultipartFile idPhoto, MultipartFile selfie)
            throws IOException {
        // Check phone is verified first
        if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
            throw new BadRequestException("Please verify your phone number first");
        }

        // Check max attempts
        if (user.getVerificationAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            throw new BadRequestException("Maximum verification attempts exceeded. Please contact support.");
        }

        // Validate files
        validateFile(idPhoto, "ID photo");
        validateFile(selfie, "Selfie");

        // Delete old files if resubmitting
        if (user.getIdPhotoUrl() != null) {
            storageService.deleteFile(user.getIdPhotoUrl());
        }
        if (user.getSelfieUrl() != null) {
            storageService.deleteFile(user.getSelfieUrl());
        }

        // Upload new files
        String idPhotoUrl = storageService.uploadFile(idPhoto, "verification/id", user.getId());
        String selfieUrl = storageService.uploadFile(selfie, "verification/selfie", user.getId());

        // Update user
        user.setIdType(idType);
        user.setIdPhotoUrl(idPhotoUrl);
        user.setSelfieUrl(selfieUrl);
        user.setVerificationStatus(VerificationStatus.PENDING_ID);
        user.setVerificationSubmittedAt(Instant.now());
        user.setVerificationRejectionReason(null); // Clear any previous rejection
        user.setVerificationAttempts(user.getVerificationAttempts() + 1);
        userRepository.save(user);

        // Notify admin
        notifyAdminNewVerification(user);

        log.info("User {} submitted verification documents (attempt {})", user.getId(), user.getVerificationAttempts());
    }

    // ==================== Admin Actions ====================

    /**
     * Admin: Approve user verification.
     */
    @Transactional
    public void approveVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.getVerificationStatus() != VerificationStatus.PENDING_ID) {
            throw new BadRequestException("User is not pending ID verification");
        }

        user.setVerificationStatus(VerificationStatus.PENDING_PAYMENT);
        user.setVerificationReviewedAt(Instant.now());
        userRepository.save(user);

        log.info("Admin approved verification for user {}", userId);
    }

    /**
     * Admin: Reject user verification.
     */
    @Transactional
    public void rejectVerification(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.getVerificationStatus() != VerificationStatus.PENDING_ID) {
            throw new BadRequestException("User is not pending ID verification");
        }

        // Validate reason is from predefined list
        if (reason == null || !REJECTION_REASONS.contains(reason)) {
            throw new BadRequestException("Invalid rejection reason");
        }

        user.setVerificationStatus(VerificationStatus.REJECTED);
        user.setVerificationRejectionReason(reason);
        user.setVerificationReviewedAt(Instant.now());
        userRepository.save(user);

        log.info("Admin rejected verification for user {} with reason: {}", userId, reason);
    }

    /**
     * Get predefined rejection reasons.
     */
    public Set<String> getRejectionReasons() {
        return REJECTION_REASONS;
    }

    // ==================== Payment ====================

    public String initiateVerificationPayment(User user) {
        if (Boolean.TRUE.equals(user.getVerified())) {
            throw new BadRequestException("User is already verified");
        }

        // For enhanced flow, check status is PENDING_PAYMENT (ID approved by admin)
        if (user.getVerificationStatus() != VerificationStatus.PENDING_PAYMENT &&
            user.getVerificationStatus() != VerificationStatus.APPROVED) {
            // Allow existing flow for backwards compatibility
            if (user.getVerificationStatus() != null &&
                user.getVerificationStatus() != VerificationStatus.NONE) {
                throw new BadRequestException("Please complete ID verification before payment");
            }
        }

        String invoiceUrl = String.format(
                "https://t.me/$%s?startattach=verify_%d",
                telegramBotToken.split(":")[0],
                user.getId()
        );

        return invoiceUrl;
    }

    @Transactional
    public User confirmVerificationPayment(User user, String paymentId) {
        if (Boolean.TRUE.equals(user.getVerified())) {
            throw new BadRequestException("User is already verified");
        }

        if (paymentId == null || paymentId.isBlank()) {
            log.warn("Verification attempt without payment ID for user {}", user.getId());
            throw new BadRequestException("Payment ID is required");
        }

        Long previousUserId = usedPaymentIds.putIfAbsent(paymentId, user.getId());
        if (previousUserId != null) {
            log.warn("Duplicate payment ID {} - already used by user {}, attempted by user {}",
                    paymentId, previousUserId, user.getId());
            throw new BadRequestException("Payment has already been processed");
        }

        log.info("Verifying user {} with payment ID: {}", user.getId(), paymentId);

        user.setVerificationStatus(VerificationStatus.APPROVED);
        user.setVerified(true);
        user.setVerifiedAt(Instant.now());

        return userRepository.save(user);
    }

    // ==================== Helpers ====================

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(fieldName + " must be less than 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(fieldName + " must be a JPG, PNG, or HEIC image");
        }
    }

    private void notifyAdminNewVerification(User user) {
        if (adminTelegramId == null) {
            log.warn("Admin Telegram ID not configured - skipping verification notification");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("📋 *New Verification Request*\n\n");
        text.append("*User ID:* ").append(user.getId()).append("\n");
        text.append("*Name:* ").append(user.getFirstName());
        if (user.getLastName() != null) {
            text.append(" ").append(user.getLastName());
        }
        text.append("\n");
        if (user.getUsername() != null) {
            text.append("*Username:* @").append(user.getUsername()).append("\n");
        }
        text.append("*Phone:* ").append(user.getPhoneNumber()).append(" ✓\n");
        text.append("*ID Type:* ").append(user.getIdType()).append("\n");
        text.append("*Attempt:* ").append(user.getVerificationAttempts()).append("/").append(MAX_VERIFICATION_ATTEMPTS).append("\n\n");
        text.append("*ID Photo:* ").append(user.getIdPhotoUrl()).append("\n");
        text.append("*Selfie:* ").append(user.getSelfieUrl()).append("\n\n");
        text.append("_Submitted: ").append(user.getVerificationSubmittedAt()).append("_");

        try {
            telegramClient.sendMessage(adminTelegramId, text.toString(), "Markdown", null);
            log.info("Sent verification notification to admin for user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send verification notification to admin", e);
        }
    }
}
