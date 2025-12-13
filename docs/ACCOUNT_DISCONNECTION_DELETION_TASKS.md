# Account Disconnection & Deletion Implementation Tasks

## Overview
This document outlines tasks for implementing:
- **Account Linking** - Auto-link OAuth accounts to prevent duplicates
- **Account Disconnection** - Unlink OAuth accounts
- **Account Deletion** - Soft delete with data retention
- **Account Merge** - Merge duplicate accounts (admin tool)

**Security Goal**: Prevent users from resetting reputation/trust scores by deleting and recreating accounts.

**Compliance Goal**: Meet GDPR/CCPA requirements for data deletion while maintaining security.

---

## BACKEND TASKS

### Phase 1: Database Schema Updates

#### Task B1: Add Soft Delete Fields to User Entity
**Priority**: High

Add fields to support soft deletion and data retention tracking.

**Fields to Add** (Java/JPA):
```java
@Column(name = "deleted")
private Boolean deleted = false;

@Column(name = "deleted_at")
private Instant deletedAt;

@Column(name = "deletion_reason")
@Enumerated(EnumType.STRING)
private DeletionReason deletionReason; // USER_REQUEST, ADMIN_BAN, SUSPENDED

@Column(name = "anonymized")
private Boolean anonymized = false;

@Column(name = "anonymized_at")
private Instant anonymizedAt;

@Column(name = "retention_until")
private Instant retentionUntil;
```

**Migration**: Create `V7__add_soft_delete_fields.sql`

**Acceptance Criteria**:
- [ ] Migration script created and tested
- [ ] All new fields have appropriate defaults
- [ ] Indexes added for `deleted`, `deleted_at`, `retention_until`

---

#### Task B2: Create OAuth ID Tracking Table
**Priority**: High

Create a separate table to track OAuth IDs permanently (survives user hard-deletion) to prevent account recreation abuse.

**Entity**: `OAuthIdRecord`
```java
@Entity
@Table(name = "oauth_id_records")
public class OAuthIdRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "telegram_user_id", unique = true)
    private Long telegramUserId;

    @Column(name = "user_id") // NOT a foreign key - user may be deleted
    private Long userId;

    @Column(name = "deleted")
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "permanently_blocked")
    private Boolean permanentlyBlocked = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

**Note**: `userId` is NOT a foreign key because the User record may be hard-deleted later while we keep this tracking record.

**Acceptance Criteria**:
- [ ] Table created with proper indexes
- [ ] Unique constraints on `google_id` and `telegram_user_id`
- [ ] No foreign key to User table (intentional)

---

### Phase 2: Auto-Link Prevention (Prevents Duplicate Accounts)

#### Task B3: Update Google Login to Auto-Link When Authenticated
**Priority**: Critical

Modify `/api/auth/google` to auto-link accounts when user is already authenticated, preventing duplicate account creation.

**Current Behavior**: Creates new user if `googleId` not found
**New Behavior**: If JWT present, link to current user instead of creating new

**Business Logic**:
```
1. Validate Google ID token
2. Extract googleId from token

3. IF request has valid JWT token (user already logged in):
    a. Get current user from JWT
    b. Check if googleId is already linked to ANY user:
        - If linked to SAME user → Return success (already linked)
        - If linked to DIFFERENT user → Return 409 "This Google account is linked to another user"
        - If NOT linked → Link googleId to current user, return updated user

4. ELSE (no JWT - normal login flow):
    a. Check OAuth tracking table for blocked/deleted status
    b. Find or create user by googleId (existing logic)
    c. Handle deleted account restoration (see Task B6)
```

**Acceptance Criteria**:
- [ ] Auto-links when JWT present and googleId available
- [ ] Returns 409 if googleId belongs to different user
- [ ] Falls back to normal login when no JWT
- [ ] Logs linking action for audit

---

#### Task B4: Update Telegram Login to Auto-Link When Authenticated
**Priority**: Critical

Same as Task B3 but for `/api/auth/telegram` and `/api/auth/telegram-web`.

**Business Logic**: Same as Task B3, using `telegramUserId` instead of `googleId`.

**Acceptance Criteria**:
- [ ] Auto-links when JWT present and telegramUserId available
- [ ] Returns 409 if telegramUserId belongs to different user
- [ ] Falls back to normal login when no JWT
- [ ] Logs linking action for audit

---

### Phase 3: Account Disconnection

#### Task B5: Implement Unlink Google Endpoint
**Priority**: High

**Endpoint**: `DELETE /api/auth/unlink-google`

Allow users to disconnect their Google account, preventing unlinking if it's the last connected account.

**Business Logic**:
1. Verify user is authenticated (JWT required)
2. Check if user has Google account linked
3. Check if user has at least one OTHER login method (Telegram)
4. If only Google is linked → Return 400 "Cannot unlink last connected account"
5. If both are linked → Clear `googleId` and `googleEmail`
6. Return updated user

**Response Codes**:
- 200: Success, returns updated user
- 400: Cannot unlink last account
- 401: Unauthorized
- 404: Google account not linked

**Acceptance Criteria**:
- [ ] Prevents unlinking last account
- [ ] Clears googleId and googleEmail
- [ ] Logs action for audit

---

#### Task B6: Implement Unlink Telegram Endpoint
**Priority**: High

**Endpoint**: `DELETE /api/auth/unlink-telegram`

Same as Task B5 but for Telegram.

**Additional Logic**: Also clear `username` if it came from Telegram and user hasn't manually set one.

**Acceptance Criteria**:
- [ ] Prevents unlinking last account
- [ ] Clears telegramUserId and username (if from Telegram)
- [ ] Logs action for audit

---

### Phase 4: Account Deletion

#### Task B7: Implement Account Deletion Endpoint
**Priority**: High

**Endpoint**: `DELETE /api/me/account`

Soft delete user account with immediate PII anonymization.

**Business Logic**:
1. Verify user is authenticated
2. **Record OAuth IDs in tracking table** (for recreation prevention):
   - Create/update OAuthIdRecord with current googleId and telegramUserId
   - Set `deleted = true`, `deletedAt = now`
   - Set `blockedUntil = now + 1 year`
3. **Immediate Anonymization**:
   - `firstName = "Deleted"`
   - `lastName = "User"`
   - `googleEmail = null`
   - `phoneNumber = null`
   - `username = "deleted_" + id`
4. **Soft Delete**:
   - `deleted = true`
   - `deletedAt = now`
   - `deletionReason = USER_REQUEST`
5. **Clear OAuth Links**:
   - `googleId = null`
   - `telegramUserId = null`
6. **Set Retention Period**:
   - `retentionUntil = now + 2 years` (configurable)
7. **Anonymize Related Data**:
   - Update trips: set contact info to null where `user_id = userId`
   - Update requests: set sender contact info to null where `sender_user_id = userId`
8. **Handle Reports**:
   - Keep reports filed BY this user (for platform safety)
   - Keep reports filed AGAINST this user (for platform safety)
   - Anonymize reporter/reported names in report records
9. Invalidate all user sessions (blacklist JWT)
10. **Send notification email** (if Google email was set, send before clearing)

**Response**: `{ message: "Account deleted successfully", deletedAt: timestamp }`

**Acceptance Criteria**:
- [ ] PII anonymized immediately
- [ ] OAuth IDs recorded in tracking table
- [ ] Related data anonymized
- [ ] Reports preserved but anonymized
- [ ] Email notification sent (if applicable)
- [ ] Sessions invalidated

---

#### Task B8: Update Login to Handle Deleted Accounts
**Priority**: High

Modify Google and Telegram login endpoints to handle deleted accounts.

**Business Logic** (add to existing login flow):
```
After validating OAuth token, before creating/returning user:

1. Check OAuthIdRecord table for this OAuth ID
2. IF record exists AND deleted = true:
    a. IF permanentlyBlocked = true:
        → Return 403 "This account has been permanently banned"

    b. IF blockedUntil > now:
        → Return 403 "Account recreation blocked. Try again after {blockedUntil}"

    c. IF blockedUntil <= now (block period expired):
        → Allow new account creation
        → Update OAuthIdRecord: deleted = false, link to new user
        → Log: "New account created for previously deleted OAuth ID"

3. IF record exists AND deleted = false:
    → Normal login flow

4. IF no record exists:
    → Create OAuthIdRecord for tracking
    → Normal login flow
```

**Note**: Account restoration within 90 days happens automatically - when a deleted user logs in within 90 days, we restore their account instead of creating a new one. Check `users` table for soft-deleted record with matching OAuth ID.

**Acceptance Criteria**:
- [ ] Blocks permanently banned accounts
- [ ] Enforces recreation block period (1 year)
- [ ] Allows recreation after block period
- [ ] Auto-restores accounts within 90 days
- [ ] Creates tracking records for new users

---

### Phase 5: Account Merge (Admin Tool)

#### Task B9: Implement Account Merge Endpoint
**Priority**: Medium

**Endpoint**: `POST /api/admin/users/merge`

Admin endpoint to merge duplicate accounts (like the current situation with User 2 and User 8).

**Request**:
```json
{
  "sourceUserId": 8,      // Account to merge FROM (will be deleted)
  "targetUserId": 2,      // Account to merge INTO (will be kept)
  "mergeOptions": {
    "transferTrips": true,
    "transferRequests": true,
    "transferReviews": true,
    "copyOAuthIds": true
  }
}
```

**Business Logic**:
1. Verify admin authentication
2. Validate both users exist
3. Check for conflicts (e.g., both have googleId set)
4. **Transfer OAuth IDs** (if copyOAuthIds):
   - Copy `googleId`, `googleEmail` from source to target (if target doesn't have)
   - Copy `telegramUserId`, `username` from source to target (if target doesn't have)
5. **Transfer Data** (based on options):
   - Update `trips.user_id` from source to target
   - Update `item_requests.sender_user_id` from source to target
   - Update reviews, notifications, etc.
6. **Delete Source Account**:
   - Hard delete source user (no soft delete needed, data transferred)
7. Return merged user

**Acceptance Criteria**:
- [ ] Admin-only access
- [ ] Validates no OAuth ID conflicts
- [ ] Transfers all selected data
- [ ] Deletes source account
- [ ] Logs merge action for audit

---

### Phase 6: Scheduled Jobs

#### Task B10: Implement Account Anonymization Job
**Priority**: Medium

Scheduled job to fully anonymize accounts deleted > 90 days ago.

**Schedule**: Daily at 2:00 AM

**Business Logic**:
1. Find accounts where `deleted = true` AND `anonymized = false` AND `deletedAt < 90 days ago`
2. For each account:
   - Ensure all PII is cleared (double-check)
   - Set `anonymized = true`
   - Set `anonymizedAt = now`
3. Log count of anonymized accounts

**Acceptance Criteria**:
- [ ] Runs on schedule
- [ ] Only processes accounts > 90 days deleted
- [ ] Logs all actions
- [ ] Handles errors gracefully (continues with other accounts)

---

#### Task B11: Implement Permanent Deletion Job
**Priority**: Low

Scheduled job to hard-delete accounts past retention period.

**Schedule**: Weekly on Sunday at 3:00 AM

**Business Logic**:
1. Find accounts where `deleted = true` AND `retentionUntil < now`
2. For each account:
   - Hard delete related data (trips, requests, notifications)
   - **Keep OAuthIdRecord** (for security tracking)
   - Hard delete user record
3. Log count of permanently deleted accounts

**Acceptance Criteria**:
- [ ] Runs on schedule
- [ ] Only processes accounts past retention
- [ ] Preserves OAuthIdRecord for security
- [ ] Logs all actions

---

### Phase 7: Configuration

#### Task B12: Define Retention Period Constants
**Priority**: Medium

Create configuration class with all retention periods.

**File**: `src/main/java/com/habeshago/config/RetentionConfig.java`

```java
@Configuration
@ConfigurationProperties(prefix = "habeshago.retention")
public class RetentionConfig {
    private int accountRecoveryDays = 90;      // Users can restore within this period
    private int oauthBlockDays = 365;          // Prevent recreation for 1 year
    private int dataRetentionDays = 730;       // 2 years for data retention
    private int tripRetentionDays = 365;       // 1 year, then anonymize

    // getters and setters
}
```

**Acceptance Criteria**:
- [ ] Constants defined and documented
- [ ] Configurable via application.properties
- [ ] Used consistently across all endpoints and jobs

---

## FRONTEND TASKS

### Phase 1: API Client Updates

#### Task F1: Add Account Management API Methods
**Priority**: High

**File**: `src/api/client.ts`

Add methods for account disconnection and deletion:

```typescript
// Unlink accounts
unlinkGoogle: () => Promise<AppUser>
unlinkTelegram: () => Promise<AppUser>

// Delete account
deleteAccount: () => Promise<{ message: string; deletedAt: string }>
```

**Acceptance Criteria**:
- [ ] All methods added with proper error handling
- [ ] TypeScript types defined

---

### Phase 2: Auth Context Updates

#### Task F2: Add Account Management Methods to AuthContext
**Priority**: High

**File**: `src/context/AuthContext.tsx`

Add methods:
- `unlinkGoogle()` - calls API, updates user state
- `unlinkTelegram()` - calls API, updates user state
- `deleteAccount()` - calls API, clears auth state, redirects to home

**Acceptance Criteria**:
- [ ] Methods added to context
- [ ] State updates correctly after operations
- [ ] Error handling with user-friendly messages

---

### Phase 3: Connected Accounts UI Improvements

#### Task F3: Improve Link Account Button Labels
**Priority**: High

**File**: `src/components/ConnectedAccounts.tsx`

**Problem**: Current buttons show "Sign in with Google" / "Login with Telegram" which is confusing when user is already logged in.

**Solution**:
1. Add clear section label: "Link Another Account"
2. Add description text above buttons: "Link your Google account" / "Link your Telegram account"
3. For Google: Keep component but wrap with clear label
4. For Telegram: Add label above the widget

**Acceptance Criteria**:
- [ ] Clear "Link Account" labels visible
- [ ] Users understand they're linking, not signing in
- [ ] Existing functionality preserved

---

#### Task F4: Add Disconnect Buttons to Connected Accounts
**Priority**: High

**File**: `src/components/ConnectedAccounts.tsx`

Add "Disconnect" buttons for each connected account with validation.

**UI Logic**:
```
IF both Google AND Telegram connected:
    Show "Disconnect" button for each
ELSE IF only one connected:
    Show disabled "Disconnect" with tooltip: "Link another account first"
```

**Flow**:
1. User clicks "Disconnect"
2. Show confirmation dialog
3. Call API
4. Update UI on success
5. Show error if it's the last account

**Acceptance Criteria**:
- [ ] Disconnect buttons shown for connected accounts
- [ ] Validation prevents disconnecting last account
- [ ] Confirmation dialog before disconnect
- [ ] Loading states during API calls

---

### Phase 4: Account Deletion UI

#### Task F5: Create Delete Account Modal
**Priority**: High

**File**: `src/components/DeleteAccountModal.tsx` (new)

Modal with warnings and confirmation for account deletion.

**Content**:
1. Warning: "This action cannot be undone after 90 days"
2. What happens:
   - Your profile will be deleted
   - Your personal information will be anonymized
   - Your trips and requests will be anonymized
3. Recovery info: "You can restore your account within 90 days by signing in again"
4. Confirmation: Require typing "DELETE" to enable button
5. Checkbox: "I understand this cannot be undone"

**Acceptance Criteria**:
- [ ] All warnings displayed clearly
- [ ] Confirmation required (type "DELETE")
- [ ] Loading state during deletion
- [ ] Redirects to home after success

---

#### Task F6: Add Delete Account to Profile Screen
**Priority**: High

**File**: `src/screens/ProfileScreen.tsx`

Add "Delete Account" menu item at bottom of settings (red/destructive style).

**Note**: Show for ALL users (web and Telegram mini-app) - GDPR requires deletion capability.

**Acceptance Criteria**:
- [ ] Menu item added with destructive styling
- [ ] Opens DeleteAccountModal
- [ ] Visible for both web and Telegram users

---

### Phase 5: Account Restoration Handling

#### Task F7: Handle Account Restoration on Login
**Priority**: Medium

**File**: `src/context/AuthContext.tsx`

Detect when a previously deleted account is restored and show welcome message.

**Logic**:
- After successful login, check if response includes `restored: true` flag
- If restored, show toast: "Welcome back! Your account has been restored."

**Acceptance Criteria**:
- [ ] Detects restoration
- [ ] Shows welcome back message
- [ ] Normal flow continues

---

### Phase 6: Translations

#### Task F8: Add Translation Keys
**Priority**: Medium

**Files**: `src/i18n/locales/en.json`, `src/i18n/locales/am.json`

Add keys for:
- Disconnect account messages
- Delete account modal content
- Error messages
- Success messages
- Restoration messages

**Acceptance Criteria**:
- [ ] All keys in English
- [ ] All keys in Amharic
- [ ] Translations reviewed for accuracy

---

## Testing Checklist

### Backend Testing
- [ ] Auto-link when authenticated user logs in with new provider
- [ ] Block linking when OAuth ID belongs to different user
- [ ] Unlink endpoints prevent removing last account
- [ ] Deletion anonymizes PII immediately
- [ ] OAuth tracking blocks recreation within 1 year
- [ ] Account restoration within 90 days
- [ ] Scheduled jobs run correctly
- [ ] Admin merge endpoint works

### Frontend Testing
- [ ] Link buttons have clear labels
- [ ] Disconnect buttons validate last account
- [ ] Delete modal requires confirmation
- [ ] State updates after all operations
- [ ] Error messages display correctly
- [ ] Translations work

---

## Implementation Order

**Phase 1 - Critical (Prevents Future Duplicates)**:
1. B3, B4: Auto-link on login (backend)
2. F3: Improve link button labels (frontend)

**Phase 2 - Core Features**:
3. B1, B2: Database schema (backend)
4. B5, B6: Unlink endpoints (backend)
5. F1, F2: API client & context (frontend)
6. F4: Disconnect buttons (frontend)

**Phase 3 - Deletion**:
7. B7, B8: Deletion & login handling (backend)
8. F5, F6: Delete account UI (frontend)

**Phase 4 - Polish**:
9. B9: Admin merge tool (backend)
10. B10, B11, B12: Scheduled jobs & config (backend)
11. F7, F8: Restoration handling & translations (frontend)

---

## Configuration Reference

```properties
# application.properties
habeshago.retention.account-recovery-days=90
habeshago.retention.oauth-block-days=365
habeshago.retention.data-retention-days=730
habeshago.retention.trip-retention-days=365
```

---

## Notes

### Security Considerations
- OAuth ID tracking survives user deletion (prevents gaming the system)
- Banned accounts cannot be restored or recreated
- All deletion/restoration actions are logged for audit

### GDPR/CCPA Compliance
- Users can delete accounts (right to erasure)
- PII anonymized immediately on deletion
- Data retained only as long as legally required
- Telegram mini-app users can also delete (not just web users)

### Data Integrity
- Reports preserved for platform safety (anonymized)
- OAuth tracking table has no FK constraint (user may be deleted)
- Soft delete allows recovery within 90 days
