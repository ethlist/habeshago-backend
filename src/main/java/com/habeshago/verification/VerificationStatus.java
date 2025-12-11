package com.habeshago.verification;

public enum VerificationStatus {
    NONE,               // Not started
    PENDING_PHONE,      // Phone OTP sent, awaiting verification
    PENDING_ID,         // Phone verified, ID submitted, awaiting admin review
    PENDING_PAYMENT,    // ID approved by admin, awaiting payment
    APPROVED,           // Fully verified
    REJECTED            // Admin rejected ID
}
