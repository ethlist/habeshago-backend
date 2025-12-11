# HabeshaGo Backend - Project Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture](#architecture)
4. [Database Schema](#database-schema)
5. [API Endpoints](#api-endpoints)
6. [Authentication](#authentication)
7. [Notification System](#notification-system)
8. [Logging & Monitoring](#logging--monitoring)
9. [Payment Integration](#payment-integration)
10. [Enhanced Verification Flow](#enhanced-verification-flow)
11. [Configuration](#configuration)
12. [Project Structure](#project-structure)
13. [Docker Deployment](#docker-deployment)

---

## Project Overview

HabeshaGo is a platform connecting Ethiopian travelers with people who need to send items. Travelers post their upcoming trips, and senders can request to have items delivered. The platform handles:

- **Trip posting** - Travelers post routes with available capacity
- **Item requests** - Senders request deliveries on posted trips
- **Request workflow** - Accept/reject/deliver lifecycle
- **User verification** - Paid verification via Stripe
- **Reviews & ratings** - Reputation system for travelers
- **Notifications** - Telegram bot notifications

### Key Features
- Dual authentication (Telegram Mini App + Web/Email)
- Multi-language support (English + Amharic)
- Telegram bot integration for real-time notifications
- Enhanced verification flow (Phone OTP + ID + Admin review + Payment)
- Trip management (edit/cancel with notifications)
- Profile sync protection (user edits preserved from Telegram overwrites)
- Stripe/Telegram Stars payment integration
- Rate limiting for security
- Comprehensive logging for production

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.3 |
| Database (Dev) | H2 | In-memory |
| Database (Prod) | PostgreSQL | 16 |
| Migrations | Flyway | Latest |
| Build Tool | Maven | 3.9+ |
| Container | Docker | Latest |
| Reverse Proxy | Nginx | Alpine |
| SSL | Let's Encrypt | via Certbot |

### Key Dependencies
- **spring-boot-starter-web** - REST API
- **spring-boot-starter-data-jpa** - Database ORM
- **spring-boot-starter-validation** - Input validation
- **spring-boot-starter-actuator** - Health checks & metrics
- **spring-boot-starter-cache** - Caching with Caffeine
- **jjwt** (v0.12.6) - JWT token handling
- **spring-security-crypto** - BCrypt password hashing
- **stripe-java** (v26.1.0) - Stripe payment SDK
- **google-cloud-storage** (v2.30.1) - GCS file storage for ID uploads
- **logstash-logback-encoder** (v7.4) - JSON logging

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENTS                              │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ Telegram Mini   │  │    Web App      │                  │
│  │      App        │  │ (React/Next.js) │                  │
│  └────────┬────────┘  └────────┬────────┘                  │
└───────────┼────────────────────┼────────────────────────────┘
            │                    │
            ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                      NGINX (SSL)                            │
│                  Port 80 → 443 redirect                     │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  SPRING BOOT BACKEND                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              RequestLoggingFilter                     │  │
│  │         (Request ID, IP tracking, timing)             │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │               AuthInterceptor                         │  │
│  │          (JWT validation, user context)               │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  CONTROLLERS                         │   │
│  │  AuthController │ TripController │ ItemRequestCtrl   │   │
│  │  VerificationCtrl │ ReviewController │ TravelerCtrl  │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   SERVICES                           │   │
│  │  TripService │ ItemRequestService │ ReviewService    │   │
│  │  WebAuthService │ StripeService │ NotificationSvc    │   │
│  │  TelegramAuthService │ JwtService │ RateLimitSvc     │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 REPOSITORIES (JPA)                   │   │
│  │  UserRepository │ TripRepository │ ItemRequestRepo   │   │
│  │  ReviewRepository │ NotificationOutboxRepository     │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
       ┌───────────┐  ┌───────────┐  ┌───────────┐
       │ PostgreSQL│  │  Telegram │  │   Stripe  │
       │    DB     │  │   Bot API │  │    API    │
       └───────────┘  └───────────┘  └───────────┘
```

### Request Flow
1. Request hits Nginx (SSL termination)
2. `RequestLoggingFilter` adds request ID and logs
3. `AuthInterceptor` validates JWT (except public endpoints)
4. Controller handles business logic
5. Response logged with status and duration

---

## Database Schema

### Entity Relationship Diagram

```
┌─────────────────────┐
│       USERS         │
├─────────────────────┤
│ id (PK)             │
│ telegram_user_id    │──┐
│ email              │  │
│ password_hash       │  │
│ first_name          │  │
│ last_name           │  │     ┌─────────────────────┐
│ username            │  │     │       TRIPS         │
│ preferred_language  │  │     ├─────────────────────┤
│ verified            │  │     │ id (PK)             │
│ verified_at         │  ├────▶│ user_id (FK)        │
│ rating_average      │  │     │ from_city           │
│ rating_count        │  │     │ from_country        │
│ completed_trips     │  │     │ to_city             │
│ completed_deliveries│  │     │ to_country          │
│ created_at          │  │     │ departure_date      │
│ updated_at          │  │     │ arrival_date        │
└─────────────────────┘  │     │ capacity_type       │
                         │     │ max_weight_kg       │
                         │     │ notes               │
                         │     │ status              │
                         │     │ created_at          │
                         │     │ updated_at          │
                         │     └──────────┬──────────┘
                         │                │
                         │                │
┌─────────────────────┐  │     ┌──────────▼──────────┐
│      REVIEWS        │  │     │   ITEM_REQUESTS     │
├─────────────────────┤  │     ├─────────────────────┤
│ id (PK)             │  │     │ id (PK)             │
│ trip_id (FK)        │◀─┼─────│ trip_id (FK)        │
│ item_request_id(FK) │◀─┼─────│ sender_user_id (FK) │◀──┐
│ reviewer_id (FK)    │◀─┤     │ description         │   │
│ reviewed_traveler_id│  │     │ weight_kg           │   │
│ rating (1-5)        │  │     │ special_instructions│   │
│ comment             │  │     │ pickup_photo_url    │   │
│ created_at          │  │     │ delivery_photo_url  │   │
└─────────────────────┘  │     │ status              │   │
                         │     │ paid                │   │
                         │     │ created_at          │   │
                         │     │ updated_at          │   │
                         │     └─────────────────────┘   │
                         │                              │
                         │     ┌─────────────────────┐   │
                         │     │ NOTIFICATION_OUTBOX │   │
                         │     ├─────────────────────┤   │
                         └────▶│ id (PK)             │   │
                               │ user_id (FK)        │◀──┘
                               │ type                │
                               │ data (JSON)         │
                               │ status              │
                               │ retry_count         │
                               │ next_attempt_at     │
                               │ created_at          │
                               └─────────────────────┘
```

### Enums

**TripStatus:**
- `OPEN` - Accepting requests
- `IN_PROGRESS` - Trip started
- `COMPLETED` - Trip finished
- `CANCELLED` - Trip cancelled

**RequestStatus:**
- `PENDING` - Awaiting traveler response
- `ACCEPTED` - Traveler accepted
- `REJECTED` - Traveler rejected
- `CANCELLED` - Sender cancelled
- `CANCELLED_BY_TRAVELER` - Auto-cancelled when trip is cancelled
- `PICKED_UP` - Item collected
- `DELIVERED` - Delivery confirmed

**VerificationStatus:**
- `NONE` - Not started
- `PENDING_PHONE` - Awaiting phone verification
- `PENDING_ID` - Awaiting ID document review
- `PENDING_PAYMENT` - ID approved, awaiting payment
- `APPROVED` - Fully verified
- `REJECTED` - ID verification rejected

**IDType:**
- `PASSPORT` - Passport document
- `NATIONAL_ID` - National ID card
- `DRIVERS_LICENSE` - Driver's license

**CapacityType:**
- `FULL_SUITCASE` - Entire suitcase available
- `HALF_SUITCASE` - Half suitcase
- `QUARTER_SUITCASE` - Quarter suitcase
- `SMALL_ITEMS` - Only small items

**OutboxStatus:**
- `PENDING` - Queued for delivery
- `SENDING` - Currently being sent
- `SENT` - Successfully delivered
- `FAILED` - Delivery failed

---

## API Endpoints

### Authentication (`/api/auth`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/telegram` | Authenticate via Telegram initData | No |
| POST | `/web/login` | Login with email/password | No |
| POST | `/web/register` | Register with email/password | No |

**POST /api/auth/telegram**
```json
Request:
{
  "initData": "telegram-init-data-string"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "1",
    "firstName": "John",
    "lastName": "Doe",
    "preferredLanguage": "en",
    "verified": false
  }
}
```

**POST /api/auth/web/register**
```json
Request:
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe"
}

Response: Same as login
```

### User Profile (`/api`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/me` | Get current user profile | Yes |
| PUT | `/me/language` | Update preferred language | Yes |

### Trips (`/api/trips`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/trips` | Create new trip | Yes |
| GET | `/trips/my` | Get my trips | Yes |
| GET | `/trips/{id}` | Get trip by ID | Yes |
| GET | `/trips/search` | Search trips | Yes |
| PUT | `/trips/{id}` | Edit trip (only if no accepted requests) | Yes |
| POST | `/trips/{id}/cancel` | Cancel trip (with notifications) | Yes |
| POST | `/trips/{id}/complete` | Mark trip complete | Yes |

**POST /api/trips**
```json
Request:
{
  "fromCity": "Washington DC",
  "fromCountry": "USA",
  "toCity": "Addis Ababa",
  "toCountry": "Ethiopia",
  "departureDate": "2024-02-15",
  "arrivalDate": "2024-02-16",
  "capacityType": "HALF_SUITCASE",
  "maxWeightKg": 15.0,
  "notes": "Flying Ethiopian Airlines"
}

Response:
{
  "id": 1,
  "traveler": { ... },
  "fromCity": "Washington DC",
  "toCity": "Addis Ababa",
  "departureDate": "2024-02-15",
  "status": "OPEN",
  "capacityType": "HALF_SUITCASE"
}
```

**GET /api/trips/search?from=DC&to=Addis&date=2024-02-15**

### Item Requests (`/api`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/trips/{tripId}/requests` | Create request for trip | Yes |
| GET | `/trips/{tripId}/requests` | Get requests for trip | Yes |
| GET | `/requests/my` | Get my sent requests | Yes |
| GET | `/requests/{id}` | Get request by ID | Yes |
| POST | `/requests/{id}/accept` | Accept request (traveler) | Yes |
| POST | `/requests/{id}/reject` | Reject request (traveler) | Yes |
| POST | `/requests/{id}/cancel` | Cancel request (sender) | Yes |
| POST | `/requests/{id}/delivered` | Mark delivered (traveler) | Yes |

**POST /api/trips/{tripId}/requests**
```json
Request:
{
  "description": "Traditional coffee set, fragile",
  "weightKg": 2.5,
  "specialInstructions": "Please handle with care"
}

Response:
{
  "id": 1,
  "tripId": 5,
  "sender": { ... },
  "description": "Traditional coffee set, fragile",
  "status": "PENDING"
}
```

### Reviews (`/api/reviews`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/` | Create review after delivery | Yes |
| GET | `/traveler/{userId}` | Get reviews for traveler | Yes |

**POST /api/reviews**
```json
Request:
{
  "requestId": 1,
  "rating": 5,
  "comment": "Excellent service, item delivered safely!"
}

Response:
{
  "id": 1,
  "rating": 5,
  "comment": "Excellent service, item delivered safely!",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Traveler Profile (`/api/travelers`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/{userId}` | Get traveler public profile | Yes |

### Verification (`/api/verification`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/phone/send` | Send OTP to phone number | Yes |
| POST | `/phone/verify` | Verify OTP code | Yes |
| POST | `/id/submit` | Submit ID documents (multipart) | Yes |
| GET | `/status` | Get verification status | Yes |
| GET | `/rejection-reasons` | Get predefined rejection reasons | Yes |
| POST | `/payment` | Initiate Telegram payment | Yes |
| POST | `/confirm` | Confirm Telegram payment | Yes |
| POST | `/stripe/checkout` | Create Stripe checkout session | Yes |
| POST | `/stripe/confirm` | Confirm Stripe payment | Yes |

**POST /api/verification/phone/send**
```json
Request:
{
  "phoneNumber": "+1234567890"
}

Response:
{
  "success": true,
  "message": "Verification code sent",
  "cooldownSeconds": null,
  "devOtp": "123456"  // Only in dev mode
}
```

**POST /api/verification/phone/verify**
```json
Request:
{
  "phoneNumber": "+1234567890",
  "otp": "123456"
}

Response:
{
  "success": true,
  "message": "Phone number verified"
}
```

**POST /api/verification/id/submit** (multipart/form-data)
```
idType: PASSPORT | NATIONAL_ID | DRIVERS_LICENSE
idPhoto: [file]
selfie: [file]
```

**GET /api/verification/status**
```json
Response:
{
  "status": "PENDING_ID",
  "phoneVerified": true,
  "idVerified": false,
  "rejectionReason": null,
  "verificationAttempts": 1
}
```

**POST /api/verification/stripe/checkout**
```json
Response:
{
  "sessionId": "cs_test_a1b2c3..."
}
```

### Health Check (`/actuator`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Application health status |
| GET | `/info` | Application info |

---

## Authentication

### Dual Authentication System

The application supports two authentication methods:

#### 1. Telegram Mini App Authentication
- Validates Telegram's `initData` using HMAC-SHA256
- Creates user automatically on first login
- Uses Telegram user ID as unique identifier

```java
// TelegramAuthService validates initData
String hash = extractHash(initData);
String checkString = buildCheckString(params);
String calculatedHash = hmacSha256(checkString, secretKey);
if (!hash.equals(calculatedHash)) {
    throw new InvalidTelegramDataException();
}
```

#### 2. Web Authentication (Email/Password)
- BCrypt password hashing (cost factor 10)
- Rate limiting on login attempts
- Account lockout after 5 failed attempts

```java
// WebAuthService handles registration
String hashedPassword = passwordEncoder.encode(password);
user.setPasswordHash(hashedPassword);
```

### JWT Token Structure

```json
{
  "sub": "1",                    // User ID
  "telegramUserId": 123456789,   // Optional
  "iat": 1704110400,             // Issued at
  "exp": 1704196800              // Expires (24 hours)
}
```

### Rate Limiting

| Action | Limit | Window | Lockout |
|--------|-------|--------|---------|
| Login | 5 attempts | 15 min | 15 min |
| Registration | 3 attempts | 1 hour | None |

---

## Notification System

### Outbox Pattern

The notification system uses the transactional outbox pattern for reliable delivery:

1. **Enqueue** - Business logic adds notification to outbox table
2. **Process** - Scheduled job polls outbox every 10 seconds
3. **Send** - TelegramClient sends message via Bot API
4. **Retry** - Failed messages retry with exponential backoff

```
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   Business    │───▶│    Outbox     │───▶│   Telegram    │
│    Logic      │    │    Table      │    │   Bot API     │
└───────────────┘    └───────────────┘    └───────────────┘
                            │
                            ▼
                     ┌───────────────┐
                     │   Scheduled   │
                     │   Processor   │
                     │  (10s cycle)  │
                     └───────────────┘
```

### Notification Types

| Type | Trigger | Recipient |
|------|---------|-----------|
| `NEW_REQUEST` | Request created | Traveler |
| `REQUEST_ACCEPTED` | Traveler accepts | Sender |
| `REQUEST_REJECTED` | Traveler rejects | Sender |
| `TRIP_CANCELLED` | Trip cancelled by traveler | All affected senders |
| `ITEM_DELIVERED` | Delivery confirmed | Sender |
| `NEW_REVIEW` | Review submitted | Traveler |

### Message Formatting

Messages are formatted in the user's preferred language (EN/AM) with:
- Localized templates
- Inline keyboard buttons for actions
- Markdown formatting support

---

## Logging & Monitoring

### Log Levels by Environment

| Environment | Level | Format |
|-------------|-------|--------|
| Development | DEBUG | Human-readable |
| Production | INFO | JSON (ELK-compatible) |

### Log Files (Production)

| File | Content | Retention |
|------|---------|-----------|
| `app.log` | All application logs | 7 days |
| `security-audit.log` | Security events only | 90 days |

### Request Logging

Every HTTP request is logged with:
- **Request ID** - Unique identifier for correlation
- **IP Address** - Client IP (X-Forwarded-For aware)
- **Method/Path** - HTTP method and endpoint
- **Duration** - Processing time in milliseconds
- **Status Code** - HTTP response status

```
2024-01-15 10:30:45.123 INFO [abc12345] POST /api/auth/web/login | status=200 | duration=156ms | ip=192.168.1.1
```

### Security Audit Events

| Event | Log Level | Details |
|-------|-----------|---------|
| LOGIN_SUCCESS | INFO | User ID, IP |
| LOGIN_FAILURE | WARN | Email (masked), IP, reason |
| REGISTRATION_SUCCESS | INFO | User ID, IP |
| RATE_LIMIT_EXCEEDED | ERROR | IP, endpoint |
| RATE_LIMIT_LOCKOUT | ERROR | IP, duration |
| TOKEN_INVALID | WARN | IP |
| AUTHORIZATION_FAILURE | WARN | User ID, resource |

### Email Masking
Emails are masked in logs for privacy:
- `john.doe@gmail.com` → `jo***@gmail.com`

---

## Payment Integration

### Stripe Integration

Used for web user verification payments.

**Flow:**
1. Frontend calls `/api/verification/stripe/checkout`
2. Backend creates Stripe Checkout Session
3. Frontend redirects to Stripe hosted checkout
4. User completes payment
5. Frontend calls `/api/verification/stripe/confirm` with session ID
6. Backend verifies payment and marks user as verified

**Configuration:**
- `STRIPE_SECRET_KEY` - API key for server-side calls
- `STRIPE_WEBHOOK_SECRET` - For webhook signature verification

### Telegram Stars (Future)

For Telegram Mini App users, native Telegram Stars payment can be used.

---

## Enhanced Verification Flow

The verification system uses a multi-step process to verify user identity.

### Verification Steps

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Phone OTP      │───▶│  ID Document    │───▶│  Admin Review   │───▶│  Payment        │
│  (Twilio SMS)   │    │  Upload (GCS)   │    │  (Telegram)     │    │  (Stripe/Stars) │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
     NONE →             PENDING_PHONE →        PENDING_ID →           PENDING_PAYMENT →
     PENDING_PHONE      PENDING_ID             PENDING_PAYMENT        APPROVED
```

### Step 1: Phone Verification
- User submits phone number
- OTP sent via Twilio SMS (or logged in dev mode)
- 60-second cooldown between OTP requests
- OTP valid for 5 minutes

### Step 2: ID Document Upload
- User uploads ID photo and selfie
- Files stored in GCS (or local in dev mode)
- Supports PASSPORT, NATIONAL_ID, DRIVERS_LICENSE
- Max 5MB per file, JPG/PNG/HEIC formats
- Max 3 verification attempts

### Step 3: Admin Review
- Admin notified via Telegram with user details and document URLs
- Admin can approve or reject with predefined reasons:
  - "ID photo is blurry or unreadable"
  - "Selfie doesn't clearly show your face"
  - "Face in selfie doesn't match ID photo"
  - "ID appears to be expired"
  - "Name on ID doesn't match account name"

### Step 4: Payment
- After admin approval, user can pay verification fee
- Supports Stripe (web) or Telegram Stars (mini app)
- Once paid, user is fully verified

### Profile Sync Protection

When users edit their profile (firstName, lastName), a `profileEditedByUser` flag is set.
Subsequent Telegram logins will not overwrite user-edited fields, preserving their changes.

---

## Configuration

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile (dev/prod) | Yes | dev |
| `DATABASE_URL` | PostgreSQL JDBC URL | Prod | H2 in-memory |
| `DATABASE_USERNAME` | Database user | Prod | sa |
| `DATABASE_PASSWORD` | Database password | Prod | - |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API token | Yes | - |
| `JWT_SECRET` | Secret for JWT signing (64+ chars) | Yes | - |
| `STRIPE_SECRET_KEY` | Stripe API secret key | No | - |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook secret | No | - |
| `TWILIO_ACCOUNT_SID` | Twilio account SID for SMS | No | - |
| `TWILIO_AUTH_TOKEN` | Twilio auth token | No | - |
| `TWILIO_FROM_NUMBER` | Twilio phone number | No | - |
| `GCS_BUCKET_NAME` | GCS bucket for ID uploads | No | - |
| `GCS_PROJECT_ID` | GCS project ID | No | - |
| `ADMIN_TELEGRAM_ID` | Admin Telegram ID for notifications | No | - |
| `HABESHAGO_CORS_ALLOWED_ORIGINS` | CORS allowed origins | No | * |

### Application Profiles

**Development (default):**
- H2 in-memory database
- Console logging
- All actuator endpoints exposed

**Production:**
- PostgreSQL database
- JSON file logging
- Limited actuator endpoints
- CORS restricted

### Configuration Files

```
src/main/resources/
├── application.properties      # Common settings
├── application-dev.properties  # Development overrides
├── application-prod.properties # Production overrides
└── logback-spring.xml          # Logging configuration
```

---

## Project Structure

```
habeshago/
├── src/
│   └── main/
│       ├── java/com/habeshago/
│       │   ├── HabeshaGoApplication.java     # Main entry point
│       │   │
│       │   ├── auth/                         # Authentication module
│       │   │   ├── AuthController.java       # Auth endpoints
│       │   │   ├── AuthInterceptor.java      # JWT validation filter
│       │   │   ├── JwtService.java           # JWT generation/validation
│       │   │   ├── TelegramAuthService.java  # Telegram auth
│       │   │   ├── WebAuthService.java       # Email/password auth
│       │   │   ├── RateLimitService.java     # Rate limiting
│       │   │   └── dto/
│       │   │       ├── AuthResponse.java
│       │   │       ├── TelegramAuthRequest.java
│       │   │       ├── WebLoginRequest.java
│       │   │       └── WebRegisterRequest.java
│       │   │
│       │   ├── user/                         # User module
│       │   │   ├── User.java                 # User entity
│       │   │   ├── UserRepository.java       # JPA repository
│       │   │   ├── UserDto.java              # User DTO
│       │   │   ├── TravelerController.java   # Public profile endpoints
│       │   │   ├── TravelerService.java      # Traveler profile logic
│       │   │   └── dto/
│       │   │       ├── TravelerInfoDto.java
│       │   │       ├── TravelerProfileDto.java
│       │   │       └── SenderInfoDto.java
│       │   │
│       │   ├── trip/                         # Trip module
│       │   │   ├── Trip.java                 # Trip entity
│       │   │   ├── TripStatus.java           # Trip status enum
│       │   │   ├── CapacityType.java         # Capacity type enum
│       │   │   ├── TripRepository.java       # JPA repository
│       │   │   ├── TripService.java          # Trip business logic
│       │   │   ├── TripController.java       # Trip endpoints
│       │   │   └── dto/
│       │   │       ├── TripDto.java
│       │   │       ├── TripCreateRequest.java
│       │   │       └── TripSearchRequest.java
│       │   │
│       │   ├── request/                      # Item request module
│       │   │   ├── ItemRequest.java          # Request entity
│       │   │   ├── RequestStatus.java        # Request status enum
│       │   │   ├── ItemRequestRepository.java
│       │   │   ├── ItemRequestService.java   # Request business logic
│       │   │   ├── ItemRequestController.java
│       │   │   └── dto/
│       │   │       ├── ItemRequestDto.java
│       │   │       └── ItemRequestCreateRequest.java
│       │   │
│       │   ├── review/                       # Review module
│       │   │   ├── Review.java               # Review entity
│       │   │   ├── ReviewRepository.java
│       │   │   ├── ReviewService.java        # Review business logic
│       │   │   ├── ReviewController.java
│       │   │   └── dto/
│       │   │       ├── ReviewDto.java
│       │   │       └── CreateReviewRequest.java
│       │   │
│       │   ├── verification/                 # Verification/payment module
│       │   │   ├── VerificationController.java
│       │   │   ├── VerificationService.java  # Full verification flow
│       │   │   ├── VerificationStatus.java   # Verification status enum
│       │   │   ├── IDType.java               # ID type enum
│       │   │   ├── SmsService.java           # Twilio SMS OTP
│       │   │   ├── StorageService.java       # GCS file storage
│       │   │   ├── StripeService.java        # Stripe integration
│       │   │   └── dto/
│       │   │       ├── PhoneSendOtpRequest.java
│       │   │       ├── PhoneVerifyOtpRequest.java
│       │   │       ├── OtpResponse.java
│       │   │       ├── VerificationStatusResponse.java
│       │   │       ├── InitiatePaymentResponse.java
│       │   │       ├── ConfirmPaymentRequest.java
│       │   │       ├── StripeCheckoutResponse.java
│       │   │       └── StripeConfirmRequest.java
│       │   │
│       │   ├── notification/                 # Notification module
│       │   │   ├── NotificationOutbox.java   # Outbox entity
│       │   │   ├── NotificationType.java     # Notification types
│       │   │   ├── OutboxStatus.java         # Outbox status
│       │   │   ├── NotificationOutboxRepository.java
│       │   │   └── NotificationService.java  # Outbox processor
│       │   │
│       │   ├── telegram/                     # Telegram integration
│       │   │   ├── TelegramClient.java       # Bot API client
│       │   │   ├── TelegramMessage.java      # Message model
│       │   │   ├── TelegramMessageFormatter.java
│       │   │   └── InlineKeyboardButton.java
│       │   │
│       │   ├── common/                       # Shared components
│       │   │   ├── GlobalExceptionHandler.java
│       │   │   ├── RequestLoggingFilter.java  # HTTP logging
│       │   │   ├── SecurityAuditLogger.java   # Security logging
│       │   │   ├── ApiError.java
│       │   │   ├── NotFoundException.java
│       │   │   ├── BadRequestException.java
│       │   │   ├── ForbiddenException.java
│       │   │   └── ConflictException.java
│       │   │
│       │   └── config/                       # Configuration
│       │       └── WebConfig.java            # CORS, interceptors
│       │
│       └── resources/
│           ├── application.properties
│           ├── application-dev.properties
│           ├── application-prod.properties
│           ├── logback-spring.xml
│           └── db/migration/                 # Flyway migrations
│               └── V1__initial_schema.sql
│
├── deploy/                                   # Deployment files
│   ├── docker-compose.yml                   # Container orchestration
│   ├── nginx.conf                           # Nginx configuration
│   ├── setup-vm.sh                          # VM setup script
│   └── .env.example                         # Environment template
│
├── docs/                                    # Documentation
│   ├── DEPLOYMENT_GUIDE.md
│   └── PROJECT_DOCUMENTATION.md
│
├── Dockerfile                               # Container build
├── pom.xml                                  # Maven dependencies
└── .gitignore
```

---

## Docker Deployment

### Container Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Network                           │
│                  (habeshago-network)                        │
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │   Nginx     │───▶│   Backend   │───▶│  PostgreSQL │     │
│  │  (Alpine)   │    │ (Java 21)   │    │    (16)     │     │
│  │ Port 80,443 │    │  Port 8080  │    │  Port 5432  │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│        │                                      │             │
│        ▼                                      ▼             │
│  ┌─────────────┐                       ┌─────────────┐     │
│  │   Certbot   │                       │  postgres_  │     │
│  │ (SSL certs) │                       │    data     │     │
│  └─────────────┘                       │  (volume)   │     │
│                                        └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### Dockerfile (Multi-stage Build)

```dockerfile
# Build stage - Maven with Java 21
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src src
RUN mvn package -DskipTests -B

# Runtime stage - JRE only
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
COPY --from=build /app/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose Services

| Service | Image | Purpose |
|---------|-------|---------|
| postgres | postgres:16-alpine | Database |
| backend | (built from Dockerfile) | Spring Boot API |
| nginx | nginx:alpine | Reverse proxy, SSL |
| certbot | certbot/certbot | SSL certificate management |

### Health Checks

- **PostgreSQL**: `pg_isready` every 10s
- **Backend**: HTTP GET `/actuator/health` every 30s
- **Start period**: 60s (allows time for Spring Boot startup)

### Volumes

- `postgres_data` - Persistent database storage
- `certbot_data` - SSL certificate challenge files
- `./ssl` - SSL certificates (bind mount)

---

## Development Setup

### Prerequisites
- Java 21+
- Maven 3.9+
- Git

### Running Locally

```bash
# Clone repository
git clone https://github.com/ethlist/habeshago-backend.git
cd habeshago-backend

# Run with dev profile (H2 database)
mvn spring-boot:run

# Or with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Running Tests

```bash
mvn test
```

### Building JAR

```bash
mvn package -DskipTests
java -jar target/habeshago-backend-0.0.1-SNAPSHOT.jar
```

---

## Security Considerations

### Implemented
- JWT authentication with short expiry (24h)
- BCrypt password hashing (cost 10)
- Rate limiting on auth endpoints
- SQL injection prevention (JPA parameterized queries)
- CORS configuration
- Non-root Docker user
- Input validation on all endpoints
- Security audit logging

### Recommendations for Production
- Use managed database (Cloud SQL)
- Enable SSL/TLS (implemented via nginx)
- Regular secret rotation
- Database backups
- Monitoring and alerting
- WAF (Web Application Firewall)

---

## Future Enhancements

1. **Email verification** - Verify email addresses
2. **Push notifications** - Firebase Cloud Messaging
3. **Image uploads** - Cloud Storage integration
4. **Real-time updates** - WebSocket support
5. **Admin dashboard** - User/trip management
6. **Analytics** - Usage tracking
7. **Caching layer** - Redis for sessions
