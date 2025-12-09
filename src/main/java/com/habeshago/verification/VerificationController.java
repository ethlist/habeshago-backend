package com.habeshago.verification;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.user.User;
import com.habeshago.user.UserDto;
import com.habeshago.verification.dto.ConfirmPaymentRequest;
import com.habeshago.verification.dto.InitiatePaymentResponse;
import com.habeshago.verification.dto.StripeCheckoutResponse;
import com.habeshago.verification.dto.StripeConfirmRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
