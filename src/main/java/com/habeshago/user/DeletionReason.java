package com.habeshago.user;

/**
 * Reason for account deletion/deactivation.
 */
public enum DeletionReason {
    USER_REQUEST,   // User requested deletion
    ADMIN_BAN,      // Banned by admin (cannot be restored)
    SUSPENDED       // Suspended due to violations (cannot be restored)
}
