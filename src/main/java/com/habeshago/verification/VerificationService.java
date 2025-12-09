package com.habeshago.verification;

import com.habeshago.common.BadRequestException;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class VerificationService {

    private final UserRepository userRepository;

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

        // In production, validate the payment with Telegram API
        // For now, mark the user as verified
        user.setVerified(true);
        user.setVerifiedAt(Instant.now());

        return userRepository.save(user);
    }
}
