package com.habeshago.verification;

import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StripeService {

    private final UserRepository userRepository;

    @Value("${habeshago.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${habeshago.stripe.verification-price-cents:999}")
    private Long verificationPriceCents;

    @Value("${habeshago.stripe.success-url:https://habeshago.pages.dev/verification/success}")
    private String successUrl;

    @Value("${habeshago.stripe.cancel-url:https://habeshago.pages.dev/verification/cancel}")
    private String cancelUrl;

    public StripeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
    }

    /**
     * Create a Stripe Checkout session for user verification payment
     * @return The checkout session ID
     */
    public String createCheckoutSession(User user) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new StripeNotConfiguredException("Stripe is not configured");
        }

        if (user.getVerified() != null && user.getVerified()) {
            throw new AlreadyVerifiedException("User is already verified");
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .setCustomerEmail(user.getEmail())
                    .putMetadata("user_id", user.getId().toString())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("usd")
                                                    .setUnitAmount(verificationPriceCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("HabeshaGo Verification")
                                                                    .setDescription("Become a verified traveler on HabeshaGo")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);
            return session.getId();

        } catch (StripeException e) {
            throw new StripePaymentException("Failed to create checkout session: " + e.getMessage(), e);
        }
    }

    /**
     * Confirm payment and mark user as verified
     * @param sessionId The Stripe checkout session ID
     * @return Updated user
     */
    @Transactional
    public User confirmPayment(String sessionId, User user) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new StripeNotConfiguredException("Stripe is not configured");
        }

        try {
            // Retrieve the session to verify payment status
            Session session = Session.retrieve(sessionId);

            // Verify payment was successful
            if (!"complete".equals(session.getStatus())) {
                throw new PaymentNotCompletedException("Payment has not been completed");
            }

            if (!"paid".equals(session.getPaymentStatus())) {
                throw new PaymentNotCompletedException("Payment status is not paid");
            }

            // Verify the session belongs to this user
            String sessionUserId = session.getMetadata().get("user_id");
            if (sessionUserId == null || !sessionUserId.equals(user.getId().toString())) {
                throw new PaymentVerificationException("Session does not belong to this user");
            }

            // Mark user as verified
            user.setVerified(true);
            user.setVerifiedAt(Instant.now());

            return userRepository.save(user);

        } catch (StripeException e) {
            throw new StripePaymentException("Failed to verify payment: " + e.getMessage(), e);
        }
    }

    // Exception classes
    public static class StripeNotConfiguredException extends RuntimeException {
        public StripeNotConfiguredException(String message) {
            super(message);
        }
    }

    public static class AlreadyVerifiedException extends RuntimeException {
        public AlreadyVerifiedException(String message) {
            super(message);
        }
    }

    public static class StripePaymentException extends RuntimeException {
        public StripePaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PaymentNotCompletedException extends RuntimeException {
        public PaymentNotCompletedException(String message) {
            super(message);
        }
    }

    public static class PaymentVerificationException extends RuntimeException {
        public PaymentVerificationException(String message) {
            super(message);
        }
    }
}
