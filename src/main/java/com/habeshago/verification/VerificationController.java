package com.habeshago.verification;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.user.User;
import com.habeshago.user.UserDto;
import com.habeshago.verification.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;
    private final StripeService stripeService;

    public VerificationController(VerificationService verificationService, StripeService stripeService) {
        this.verificationService = verificationService;
        this.stripeService = stripeService;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    // ==================== Phone Verification ====================

    /**
     * Send OTP to phone number for verification.
     */
    @PostMapping("/phone/send")
    public ResponseEntity<OtpResponse> sendOtp(
            HttpServletRequest request,
            @Valid @RequestBody PhoneSendOtpRequest body) {
        User user = requireCurrentUser(request);
        try {
            String devOtp = verificationService.sendOtp(user, body.getPhoneNumber());
            if (devOtp != null) {
                // Dev mode - include OTP in response for testing
                return ResponseEntity.ok(OtpResponse.successWithDevOtp("Verification code sent", devOtp));
            }
            return ResponseEntity.ok(OtpResponse.success("Verification code sent"));
        } catch (SmsService.CooldownException e) {
            long cooldown = verificationService.getOtpCooldown(body.getPhoneNumber());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(OtpResponse.cooldown(cooldown));
        }
    }

    /**
     * Verify OTP and mark phone as verified.
     */
    @PostMapping("/phone/verify")
    public ResponseEntity<OtpResponse> verifyOtp(
            HttpServletRequest request,
            @Valid @RequestBody PhoneVerifyOtpRequest body) {
        User user = requireCurrentUser(request);
        boolean verified = verificationService.verifyOtp(user, body.getPhoneNumber(), body.getOtp());
        if (verified) {
            return ResponseEntity.ok(OtpResponse.success("Phone number verified"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(OtpResponse.error("Invalid or expired verification code"));
        }
    }

    // ==================== ID Verification ====================

    /**
     * Submit ID verification documents.
     */
    @PostMapping(value = "/id/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDto> submitIdVerification(
            HttpServletRequest request,
            @RequestParam("idType") String idTypeStr,
            @RequestParam("idPhoto") MultipartFile idPhoto,
            @RequestParam("selfie") MultipartFile selfie) throws IOException {
        User user = requireCurrentUser(request);

        IDType idType;
        try {
            idType = IDType.valueOf(idTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        verificationService.submitIdVerification(user, idType, idPhoto, selfie);
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Get predefined rejection reasons (for admin UI).
     */
    @GetMapping("/rejection-reasons")
    public ResponseEntity<Set<String>> getRejectionReasons() {
        return ResponseEntity.ok(verificationService.getRejectionReasons());
    }

    /**
     * Get current verification status.
     */
    @GetMapping("/status")
    public ResponseEntity<VerificationStatusResponse> getVerificationStatus(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(new VerificationStatusResponse(
                user.getVerificationStatus() != null ? user.getVerificationStatus().name() : "NONE",
                Boolean.TRUE.equals(user.getPhoneVerified()),
                Boolean.TRUE.equals(user.getVerified()),
                user.getVerificationRejectionReason(),
                user.getVerificationAttempts() != null ? user.getVerificationAttempts() : 0
        ));
    }

    // ==================== Payment ====================

    @PostMapping("/payment")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        String invoiceUrl = verificationService.initiateVerificationPayment(user);
        return ResponseEntity.ok(new InitiatePaymentResponse(invoiceUrl));
    }

    @PostMapping("/confirm")
    public ResponseEntity<UserDto> confirmPayment(
            HttpServletRequest request,
            @Valid @RequestBody ConfirmPaymentRequest body) {
        User user = requireCurrentUser(request);
        User updatedUser = verificationService.confirmVerificationPayment(user, body.paymentId());
        return ResponseEntity.ok(UserDto.from(updatedUser));
    }

    // ----- Stripe Payment Endpoints (for web users) -----

    @PostMapping("/stripe/checkout")
    public ResponseEntity<StripeCheckoutResponse> createStripeCheckout(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        String sessionId = stripeService.createCheckoutSession(user);
        return ResponseEntity.ok(new StripeCheckoutResponse(sessionId));
    }

    @PostMapping("/stripe/confirm")
    public ResponseEntity<UserDto> confirmStripePayment(
            HttpServletRequest request,
            @Valid @RequestBody StripeConfirmRequest body) {
        User user = requireCurrentUser(request);
        User updatedUser = stripeService.confirmPayment(body.sessionId(), user);
        return ResponseEntity.ok(UserDto.from(updatedUser));
    }

    // Exception handlers for Stripe errors
    @ExceptionHandler(StripeService.StripeNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleStripeNotConfigured(StripeService.StripeNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, ex.getMessage()));
    }

    @ExceptionHandler(StripeService.AlreadyVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyVerified(StripeService.AlreadyVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, ex.getMessage()));
    }

    @ExceptionHandler(StripeService.PaymentNotCompletedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotCompleted(StripeService.PaymentNotCompletedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, ex.getMessage()));
    }

    @ExceptionHandler(StripeService.PaymentVerificationException.class)
    public ResponseEntity<ErrorResponse> handlePaymentVerification(StripeService.PaymentVerificationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, ex.getMessage()));
    }

    @ExceptionHandler(StripeService.StripePaymentException.class)
    public ResponseEntity<ErrorResponse> handleStripePayment(StripeService.StripePaymentException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Payment processing error"));
    }

    public record ErrorResponse(int code, String message) {}
}
