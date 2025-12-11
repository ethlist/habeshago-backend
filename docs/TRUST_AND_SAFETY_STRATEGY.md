# HabeshaGo Trust & Safety Strategy

## Overview

This document outlines the trust and safety strategy for HabeshaGo, a platform connecting Ethiopian travelers with people who need to send items. The strategy is designed to be practical for an early-stage startup while providing meaningful protection for users.

**Core Principle**: The community IS the protection. The Ethiopian/Habesha diaspora is tight-knit, and word travels fast. Our job is to facilitate trust, not guarantee it.

---

## Business Model Context

- Platform provides **matching only**, not delivery guarantees
- Payment for delivery happens **outside the platform** (between sender and traveler directly)
- Platform charges a **verification fee** (optional, for trust badge)
- Platform is **not liable** for lost/stolen items
- Platform is **not an escrow service**

---

## The Uncomfortable Truth

1. **You cannot prevent all fraud** - A determined scammer WILL get verified, build trust, then scam
2. **Over-engineering kills adoption** - Every friction point loses users
3. **Community self-policing is powerful** - Scammers get exposed quickly in tight-knit communities
4. **Transparency > Security theater** - Real names and real reviews beat badges

---

## Phased Implementation

### Phase 1: MVP (First 500 users)

**Goal**: Get users on platform with minimal friction while establishing basic trust signals.

| Feature | Description | Priority |
|---------|-------------|----------|
| Phone verification | Basic identity via Telegram | Required |
| Telegram account linking | Reputation tied to real account | Required |
| Real names & photos | From Telegram profile | Required |
| 5-star ratings + text reviews | Social proof after delivery | Required |
| Member since date | Account age signal | Required |
| Deliveries completed count | Track record | Required |
| Completion rate | Accepted vs Completed ratio | Required |
| Manual admin review | Handle disputes personally | Required |
| Clear terms of service | "Matching only, not insurance" | Required |

**What NOT to build in Phase 1:**
- Device fingerprinting
- Risk scoring algorithms
- ID verification (optional enhancement only)
- Photo proof requirements
- New user limitations
- Category restrictions

### Phase 2: Growth (500-5000 users)

| Feature | Description |
|---------|-------------|
| ID verification for travelers | Optional, earns "ID Verified" badge |
| Optional photo proof | Earns "Photo Verified Delivery" badge |
| Completion rate tracking (detailed) | Internal metrics for risk assessment |
| Community reporting system | Crowdsourced fraud detection |
| Risk flags for admin review | Flag suspicious patterns, don't auto-block |
| Response time tracking | Shows how quickly traveler responds |

### Phase 3: Scale (5000+ users)

| Feature | Description |
|---------|-------------|
| Automated risk scoring | Volume requires automation |
| Device fingerprinting | Multi-account detection |
| New user limitations | Restrict unproven travelers |
| Category restrictions | Protect high-value items |
| ML-based fraud detection | Pattern recognition at scale |

---

## Phase 1 Detailed Implementation

### Backend Implementation Status

| Feature | Backend Status | Notes |
|---------|----------------|-------|
| Phone verification | ✅ Done | Via Telegram account |
| Telegram account linking | ✅ Done | telegramUserId in User entity |
| Real names & photos | ✅ Done | firstName, lastName from Telegram |
| Rating system | ✅ Done | Review entity, ratingAverage on User |
| Member since date | ✅ Done | createdAt field |
| Deliveries completed count | ✅ Done | completedDeliveriesCount on User |
| Completion rate | ✅ Done | Calculated from accepted/delivered |
| acceptedRequestsCount tracking | ✅ Done | Incremented in ItemRequestService.acceptRequest() |

### Frontend Implementation Needed

| Feature | Priority | Description |
|---------|----------|-------------|
| Trust signals on trip cards | P0 | Show rating, deliveries, completion rate |
| Traveler profile page | P0 | Full profile with reviews |
| Submit review flow | P0 | After delivery is marked complete |
| Completion rate badge | P1 | Color-coded percentage |
| "New Traveler" badge | P1 | For users with < 3 deliveries |

---

### 1. Trust Signals to Display

For each traveler, show:

```
┌─────────────────────────────────────────────────────────┐
│  [Photo]  Abebe Kebede                                  │
│           @abebe_k • Member since Oct 2024              │
│                                                         │
│  ★ 4.9  •  12 deliveries  •  92% completion rate        │
│                                                         │
│  "Delivered safely, very communicative" - Sara T.       │
│  "On time, handled with care" - Michael A.              │
└─────────────────────────────────────────────────────────┘
```

### 2. Completion Rate Calculation

```
Completion Rate = (Delivered Requests / Accepted Requests) × 100

Where:
- Accepted = status in (ACCEPTED, DELIVERED)
- Delivered = status = DELIVERED
- Excludes: PENDING, REJECTED, CANCELLED (by sender)
- Includes in denominator: CANCELLED_BY_TRAVELER (counts against them)
```

**Display Logic:**
| Completion Rate | Display | Color |
|-----------------|---------|-------|
| 95-100% | "98% completion" | Green |
| 80-94% | "85% completion" | Yellow |
| Below 80% | "72% completion" | Red |
| Less than 3 deliveries | "New traveler" | Gray |

### 3. Rating System

**Who can rate:**
- Only senders can rate travelers
- Only after request status = DELIVERED
- One rating per request

**Rating prompt:**
```
How was your experience with [Traveler Name]?
★ ★ ★ ★ ★

What went well? (optional)
[                                          ]

Any issues? (optional, private to admin)
[                                          ]
```

### 4. Review Display Rules

- Show most recent 5 reviews publicly
- Show reviewer's first name + last initial (Sara T.)
- Show review date
- Travelers cannot delete reviews
- Admin can remove inappropriate reviews

### 5. User Profile Fields

**Traveler Profile (public):**
```java
- firstName (from Telegram)
- lastName (from Telegram)
- username (from Telegram)
- photoUrl (from Telegram)
- memberSince (createdAt)
- verified (boolean - paid verification)
- completedDeliveries (count)
- completionRate (calculated)
- ratingAverage (1-5)
- ratingCount (number of reviews)
```

**Internal tracking (not displayed):**
```java
- acceptedRequestsCount
- cancelledByTravelerCount
- totalReportsReceived
- lastActiveAt
- deviceFingerprint (Phase 2)
```

### 6. Request Status Flow

```
PENDING ──────────┬──→ ACCEPTED ──→ DELIVERED ✓
                  │         │
                  │         └──→ CANCELLED_BY_TRAVELER ✗
                  │
                  ├──→ REJECTED
                  │
                  └──→ CANCELLED (by sender)
```

**Impact on Completion Rate:**
| Status | Affects Completion Rate? |
|--------|--------------------------|
| DELIVERED | Yes (positive) |
| ACCEPTED (ongoing) | Yes (in denominator) |
| CANCELLED_BY_TRAVELER | Yes (negative) |
| REJECTED | No |
| CANCELLED (by sender) | No |
| PENDING | No |

### 7. Admin Dashboard (Phase 1)

Essential views:
1. **Flagged Users** - Users with reports or low completion rate
2. **Recent Disputes** - Unresolved issues
3. **New Users** - Recently registered (manual review)
4. **Verification Queue** - Pending ID verifications

### 8. Terms of Service Key Points

Must clearly state:
- Platform provides matching service only
- Platform does not guarantee delivery
- Platform is not responsible for lost/damaged items
- Users transact at their own risk
- Disputes are between sender and traveler
- Platform may suspend accounts for violations

---

## Phase 1 API Reference

### Traveler Profile Endpoint

**GET** `/api/users/{userId}/profile`

Returns `TravelerProfileDto`:
```json
{
  "userId": "123",
  "firstName": "Abebe",
  "lastName": "Kebede",
  "username": "abebe_k",
  "verified": true,
  "verifiedAt": "2024-10-15T10:30:00Z",
  "ratingAverage": 4.8,
  "ratingCount": 15,
  "completedTripsCount": 8,
  "completedDeliveriesCount": 12,
  "completionRate": 92,
  "memberSince": "2024-08-01T00:00:00Z",
  "recentReviews": [
    {
      "rating": 5,
      "comment": "Excellent service!",
      "reviewerName": "Sara T.",
      "createdAt": "2024-11-01T14:00:00Z"
    }
  ]
}
```

### Trip List (includes traveler info)

**GET** `/api/trips/search?from=addis&to=dc`

Each trip includes `TravelerInfoDto`:
```json
{
  "id": "456",
  "firstName": "Abebe",
  "lastName": "Kebede",
  "username": "abebe_k",
  "verified": true,
  "ratingAverage": 4.8,
  "ratingCount": 15,
  "completedTripsCount": 8,
  "completedDeliveriesCount": 12,
  "completionRate": 92
}
```

### Submit Review Endpoint

**POST** `/api/reviews`

Request:
```json
{
  "requestId": 789,
  "rating": 5,
  "comment": "Great traveler, very communicative"
}
```

Validation:
- Request must be in DELIVERED status
- Reviewer must be the sender
- One review per request

---

## Phase 1 UI Component Specifications

### TravelerCard Component (used in trip listings)

```tsx
interface TravelerCardProps {
  firstName: string;
  lastName: string;
  username?: string;
  verified: boolean;
  ratingAverage: number | null;
  ratingCount: number;
  completedDeliveriesCount: number;
  completionRate: number | null;
}

// Display logic:
// - If completedDeliveriesCount < 3: Show "New Traveler" badge (gray)
// - If completionRate is null: Show "No deliveries yet"
// - If completionRate >= 95: Show green badge
// - If completionRate >= 80: Show yellow badge
// - If completionRate < 80: Show red badge (warning)
```

### Example UI Layout

```
┌────────────────────────────────────────────────────────────┐
│  [Photo]  Abebe K.  ✓ Verified                             │
│           @abebe_k                                         │
│                                                            │
│  ★ 4.8 (15 reviews)  •  12 deliveries  •  92% completion   │
│                                                            │
│  Member since Aug 2024                                     │
└────────────────────────────────────────────────────────────┘
```

### Completion Rate Color Coding

```tsx
const getCompletionRateColor = (rate: number | null, deliveries: number) => {
  if (deliveries < 3) return 'gray';      // New traveler
  if (rate === null) return 'gray';       // No data
  if (rate >= 95) return 'green';         // Excellent
  if (rate >= 80) return 'yellow';        // Good
  return 'red';                            // Warning
};

const getCompletionRateLabel = (rate: number | null, deliveries: number) => {
  if (deliveries < 3) return 'New Traveler';
  if (rate === null) return 'No history';
  return `${rate}% completion`;
};
```

### Review Display Component

```tsx
// Show first name + last initial for privacy
const formatReviewerName = (firstName: string, lastName: string) => {
  return `${firstName} ${lastName.charAt(0)}.`;
};

// Example: "Sara T." not "Sara Tesfaye"
```

---

## Phase 1 Implementation Checklist

### Backend (All Done)
- [x] User entity with trust fields (rating, completedDeliveriesCount, acceptedRequestsCount)
- [x] Completion rate calculation (getCompletionRate() method)
- [x] TravelerInfoDto returned with trips
- [x] TravelerProfileDto with recent reviews
- [x] Review submission endpoint
- [x] Request status flow (PENDING → ACCEPTED → DELIVERED)
- [x] Increment acceptedRequestsCount on accept
- [x] Increment completedDeliveriesCount on mark delivered

### Frontend (To Build)
- [ ] Trip card shows traveler trust signals
- [ ] Traveler profile page (click on traveler name)
- [ ] Completion rate badge with color coding
- [ ] "New Traveler" indicator for < 3 deliveries
- [ ] Rating stars display
- [ ] Review count display
- [ ] Recent reviews section on profile
- [ ] Submit review after delivery flow
- [ ] Member since date display

### Admin Dashboard (Phase 1 MVP)
- [ ] List users by completion rate (ascending = worst first)
- [ ] View user's full review history
- [ ] Manually flag/unflag users
- [ ] Add admin notes to users
- [ ] View recent disputes/reports

---

## Phase 1 User Flows

### Flow 1: Sender Evaluates Traveler

```
1. Sender searches for trips: GET /api/trips/search?from=addis&to=dc
2. Each trip result includes TravelerInfoDto with trust signals
3. Sender sees: name, photo, rating, deliveries, completion rate
4. Sender clicks traveler name → Full profile page
5. Profile shows: GET /api/users/{id}/profile
   - Full stats + recent reviews
6. Sender makes informed decision to request or skip
```

### Flow 2: After Delivery - Review Submission

```
1. Traveler marks request as delivered: POST /api/requests/{id}/delivered
2. System sends notification to sender: "Your item was delivered!"
3. Notification includes: "Rate your experience with [Traveler]"
4. Sender opens review screen
5. Sender submits: POST /api/reviews
   {
     "requestId": 123,
     "rating": 5,
     "comment": "Fast and careful delivery"
   }
6. System updates traveler's ratingAverage and ratingCount
7. Review appears in traveler's profile
```

### Flow 3: Request Lifecycle & Trust Tracking

```
State: PENDING
  → Traveler accepts: ACCEPTED
    - acceptedRequestsCount += 1
    - completionRate recalculated
  → Traveler rejects: REJECTED
    - No impact on stats

State: ACCEPTED
  → Traveler marks delivered: DELIVERED
    - completedDeliveriesCount += 1
    - completionRate improves
    - Sender can now submit review
  → Traveler cancels: CANCELLED_BY_TRAVELER (future status)
    - completionRate decreases (counts against them)

State: PENDING
  → Sender cancels: CANCELLED
    - No impact on traveler stats
```

---

## Trust Signals Summary

### What Users See (Phase 1)

| Signal | Where Shown | Purpose |
|--------|-------------|---------|
| Real name + photo | Profile, trip cards | Identity |
| Member since | Profile | Account age |
| Verified badge | Profile (if paid) | Investment in platform |
| Deliveries completed | Profile, trip cards | Experience |
| Completion rate | Profile, trip cards | Reliability |
| Star rating | Profile, trip cards | Quality |
| Recent reviews | Profile | Social proof |

### What Admins See

| Signal | Purpose |
|--------|---------|
| Total reports received | Risk assessment |
| Report details | Dispute context |
| All reviews (including hidden) | Full picture |
| Account activity patterns | Fraud detection |
| Completion rate over time | Trend analysis |

---

## Risk Mitigation Without Over-Engineering

### The Real Protections

1. **Community Pressure** - Ethiopian diaspora is tight-knit; scammers get exposed
2. **Telegram Identity** - Account tied to phone number
3. **Verification Fee** - Skin in the game ($10)
4. **Public Reviews** - Bad actors get called out
5. **Completion Rate** - Track record is visible
6. **Real Names** - Anonymity enables fraud

### What We DON'T Do (Yet)

- No payment handling = no escrow fraud
- No delivery guarantees = no liability
- No automated blocking = manual review prevents false positives
- No excessive verification = low friction adoption

---

## Metrics to Track

### User Trust Metrics
- Average completion rate across platform
- Average rating across platform
- % of deliveries with disputes
- % of users who complete verification

### Platform Health Metrics
- User retention after first delivery
- Repeat sender rate
- Repeat traveler rate
- Time to first delivery (new users)

### Fraud Indicators (Monitor, Don't Auto-Block)
- Users with completion rate < 70%
- Users with multiple reports
- Users with sudden activity spikes
- New accounts with high-value requests

---

## Future Considerations (Phase 2+)

### Optional Photo Proof
- Pickup photo (traveler with item)
- Delivery photo (item at destination)
- Earns "Photo Verified" badge
- Not required, but builds trust

### ID Verification Enhancement
- Government ID + selfie match
- Earns "ID Verified" badge
- Required for high-value categories (future)

### Device Fingerprinting
- Detect multi-account fraud
- Flag unusual device patterns
- Phase 2 implementation

### Risk Scoring
- Automated flagging for admin review
- Based on: completion rate, reports, activity patterns
- Never auto-block; always human review

---

## Summary

**Phase 1 Focus:**
1. Real identity (Telegram profile)
2. Visible track record (deliveries, completion rate, ratings)
3. Social proof (reviews from real people)
4. Low friction (don't over-verify)
5. Manual oversight (admin reviews disputes)
6. Clear terms (matching only, not insurance)

**The goal is not to prevent all fraud. The goal is to:**
1. Make honest behavior easy
2. Make bad behavior visible
3. Make recovery possible

Start simple. Add complexity when data shows you need it.
