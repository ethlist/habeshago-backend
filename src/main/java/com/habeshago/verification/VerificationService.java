package com.habeshago.verification;

import com.habeshago.common.BadRequestException;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final UserRepository userRepository;

    // Track used payment IDs to prevent replay attacks
    private final ConcurrentHashMap<String, Long> usedPaymentIds = new ConcurrentHashMap<>();

    @Value("${habeshago.telegram.bot-token:REPLACE_ME}")
    private String telegramBotToken;

    @Value("${habeshago.verification.price:500}")
    private int verificationPriceStars;

    public VerificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String initiateVerificationPayment(User user) {
        if (Boolean.TRUE.equals(user.getVerified())) {
            throw new BadRequestException("User is already verified");
        }

        // In production, this would create a Telegram Stars invoice
        // For now, return a mock invoice URL that the frontend can use
        // The actual payment flow uses Telegram's openInvoice method
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

        // Validate paymentId is provided
        if (paymentId == null || paymentId.isBlank()) {
            log.warn("Verification attempt without payment ID for user {}", user.getId());
            throw new BadRequestException("Payment ID is required");
        }

        // Check if payment ID was already used (prevent replay attacks)
        Long previousUserId = usedPaymentIds.putIfAbsent(paymentId, user.getId());
        if (previousUserId != null) {
            log.warn("Duplicate payment ID {} - already used by user {}, attempted by user {}",
                    paymentId, previousUserId, user.getId());
            throw new BadRequestException("Payment has already been processed");
        }

        // TODO: In production, validate the payment with Telegram Bot API:
        // 1. Call Telegram API to verify payment status
        // 2. Verify payment amount matches expected verification price
        // 3. Verify payment belongs to this user's Telegram account
        log.info("Verifying user {} with payment ID: {} (manual verification required in production)",
                user.getId(), paymentId);

        user.setVerified(true);
        user.setVerifiedAt(Instant.now());

        return userRepository.save(user);
    }
}
