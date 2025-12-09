# HabeshaGo Backend Implementation - Claude Agent Prompt

## Project Context

HabeshaGo is a Telegram Mini App backend built with:
- **Spring Boot 3.x**
- **Java 21**
- **PostgreSQL**
- **Spring Data JPA**
- **Flyway** for migrations
- **Telegram Bot API** for notifications

The application connects travelers (people flying between cities) with senders (people who need to send items). Users authenticate via Telegram Mini App `initData`, and all notifications are delivered through a Telegram bot.

---

## PART 1: Entity Updates

### 1.1 Update User Entity

**File:** `src/main/java/com/habeshago/user/User.java`

Add reputation and verification fields:

```java
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "telegram_user_id", unique = true, nullable = false)
    private Long telegramUserId;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage = "en";
    
    // Verification fields
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;
    
    @Column(name = "verified_at")
    private Instant verifiedAt;
    
    // Reputation fields (denormalized for fast reads)
    @Column(name = "rating_average")
    private Double ratingAverage;
    
    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;
    
    @Column(name = "completed_trips_count", nullable = false)
    private Integer completedTripsCount = 0;
    
    @Column(name = "completed_deliveries_count", nullable = false)
    private Integer completedDeliveriesCount = 0;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Getters and setters...
}
```

### 1.2 Update Trip Entity

**File:** `src/main/java/com/habeshago/trip/Trip.java`

Add COMPLETED status:

```java
@Entity
@Table(name = "trips", indexes = {
    @Index(name = "idx_trip_route_date", columnList = "from_city, to_city, departure_date"),
    @Index(name = "idx_trip_user", columnList = "user_id")
})
public class Trip {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "from_city", nullable = false, length = 100)
    private String fromCity;
    
    @Column(name = "from_country", length = 100)
    private String fromCountry;
    
    @Column(name = "from_airport_code", length = 10)
    private String fromAirportCode;
    
    @Column(name = "to_city", nullable = false, length = 100)
    private String toCity;
    
    @Column(name = "to_country", length = 100)
    private String toCountry;
    
    @Column(name = "to_airport_code", length = 10)
    private String toAirportCode;
    
    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;
    
    @Column(name = "arrival_date")
    private LocalDate arrivalDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;
    
    @Column(name = "max_weight_kg")
    private Double maxWeightKg;
    
    @Column(name = "notes", length = 1000)
    private String notes;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status = TripStatus.OPEN;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @OneToMany(mappedBy = "trip", fetch = FetchType.LAZY)
    private List<ItemRequest> requests = new ArrayList<>();
    
    // Lifecycle callbacks and getters/setters...
}

// Update TripStatus enum
public enum TripStatus {
    OPEN,
    PARTIALLY_BOOKED,
    FULL,
    CANCELLED,
    COMPLETED  // NEW
}
```

### 1.3 Update ItemRequest Entity

**File:** `src/main/java/com/habeshago/request/ItemRequest.java`

Add DELIVERED status and paid flag:

```java
@Entity
@Table(name = "item_requests", indexes = {
    @Index(name = "idx_request_trip", columnList = "trip_id"),
    @Index(name = "idx_request_sender", columnList = "sender_user_id")
})
public class ItemRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User senderUser;
    
    @Column(name = "description", nullable = false, length = 500)
    private String description;
    
    @Column(name = "weight_kg")
    private Double weightKg;
    
    @Column(name = "special_instructions", length = 1000)
    private String specialInstructions;
    
    @Column(name = "pickup_photo_url", length = 500)
    private String pickupPhotoUrl;
    
    @Column(name = "delivery_photo_url", length = 500)
    private String deliveryPhotoUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;
    
    @Column(name = "paid", nullable = false)
    private Boolean paid = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    // Lifecycle callbacks and getters/setters...
}

// Update RequestStatus enum
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    DELIVERED  // NEW
}
```

### 1.4 Create Review Entity

**File:** `src/main/java/com/habeshago/review/Review.java`

```java
@Entity
@Table(name = "reviews", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"item_request_id", "reviewer_id"}),
    indexes = {
        @Index(name = "idx_review_traveler", columnList = "reviewed_traveler_id"),
        @Index(name = "idx_review_created", columnList = "created_at")
    }
)
public class Review {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_request_id", nullable = false)
    private ItemRequest itemRequest;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_traveler_id", nullable = false)
    private User reviewedTraveler;
    
    @Column(name = "rating", nullable = false)
    @Min(1)
    @Max(5)
    private Integer rating;
    
    @Column(name = "comment", length = 2000)
    private String comment;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    // Getters and setters...
}
```

---

## PART 2: Flyway Migrations

### 2.1 Add User Reputation Fields

**File:** `src/main/resources/db/migration/V2__add_user_reputation_fields.sql`

```sql
-- Add verification and reputation fields to users table
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS verified BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS rating_average DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS rating_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS completed_trips_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS completed_deliveries_count INTEGER NOT NULL DEFAULT 0;

-- Add index for finding verified travelers
CREATE INDEX IF NOT EXISTS idx_user_verified ON users(verified) WHERE verified = TRUE;
```

### 2.2 Add Trip COMPLETED Status and Request Updates

**File:** `src/main/resources/db/migration/V3__add_delivery_status.sql`

```sql
-- The status columns are VARCHAR/enum, so no schema change needed
-- Just documenting that COMPLETED and DELIVERED are now valid values

-- Add paid flag to item_requests
ALTER TABLE item_requests
ADD COLUMN IF NOT EXISTS paid BOOLEAN NOT NULL DEFAULT FALSE;

-- Add photo proof columns if not exists
ALTER TABLE item_requests
ADD COLUMN IF NOT EXISTS pickup_photo_url VARCHAR(500),
ADD COLUMN IF NOT EXISTS delivery_photo_url VARCHAR(500);
```

### 2.3 Create Reviews Table

**File:** `src/main/resources/db/migration/V4__create_reviews_table.sql`

```sql
CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    item_request_id BIGINT NOT NULL REFERENCES item_requests(id),
    reviewer_id BIGINT NOT NULL REFERENCES users(id),
    reviewed_traveler_id BIGINT NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT unique_review_per_request UNIQUE (item_request_id, reviewer_id)
);

CREATE INDEX idx_review_traveler ON reviews(reviewed_traveler_id);
CREATE INDEX idx_review_created ON reviews(created_at DESC);
```

---

## PART 3: DTOs

### 3.1 User DTOs

**File:** `src/main/java/com/habeshago/user/dto/UserDto.java`

```java
public record UserDto(
    String id,
    Long telegramUserId,
    String firstName,
    String lastName,
    String username,
    String preferredLanguage,
    Boolean verified,
    Double ratingAverage,
    Integer ratingCount,
    Integer completedTripsCount,
    Integer completedDeliveriesCount
) {
    public static UserDto from(User user) {
        return new UserDto(
            user.getId().toString(),
            user.getTelegramUserId(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            user.getPreferredLanguage(),
            user.getVerified(),
            user.getRatingAverage(),
            user.getRatingCount(),
            user.getCompletedTripsCount(),
            user.getCompletedDeliveriesCount()
        );
    }
}
```

**File:** `src/main/java/com/habeshago/user/dto/TravelerInfoDto.java`

```java
public record TravelerInfoDto(
    String id,
    String firstName,
    String lastName,
    String username,
    Boolean verified,
    Double ratingAverage,
    Integer ratingCount,
    Integer completedTripsCount
) {
    public static TravelerInfoDto from(User user) {
        return new TravelerInfoDto(
            user.getId().toString(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            user.getVerified(),
            user.getRatingAverage(),
            user.getRatingCount(),
            user.getCompletedTripsCount()
        );
    }
}
```

**File:** `src/main/java/com/habeshago/user/dto/SenderInfoDto.java`

```java
public record SenderInfoDto(
    String id,
    String firstName,
    String lastName,
    String username
) {
    public static SenderInfoDto from(User user) {
        return new SenderInfoDto(
            user.getId().toString(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername()
        );
    }
}
```

### 3.2 Trip DTOs

**File:** `src/main/java/com/habeshago/trip/dto/TripDto.java`

Include traveler reputation in trip listing:

```java
public record TripDto(
    String id,
    String userId,
    String fromCity,
    String fromCountry,
    String fromAirportCode,
    String toCity,
    String toCountry,
    String toAirportCode,
    LocalDate departureDate,
    LocalDate arrivalDate,
    String capacityType,
    Double maxWeightKg,
    String notes,
    String status,
    Instant createdAt,
    Instant updatedAt,
    // Traveler info with reputation
    TravelerInfoDto traveler,
    // Request counts
    Integer requestCount,
    Integer pendingRequestCount
) {
    public static TripDto from(Trip trip) {
        return from(trip, true);
    }
    
    public static TripDto from(Trip trip, boolean includeTraveler) {
        long totalRequests = trip.getRequests() != null ? trip.getRequests().size() : 0;
        long pendingRequests = trip.getRequests() != null 
            ? trip.getRequests().stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING)
                .count() 
            : 0;
        
        return new TripDto(
            trip.getId().toString(),
            trip.getUser().getId().toString(),
            trip.getFromCity(),
            trip.getFromCountry(),
            trip.getFromAirportCode(),
            trip.getToCity(),
            trip.getToCountry(),
            trip.getToAirportCode(),
            trip.getDepartureDate(),
            trip.getArrivalDate(),
            trip.getCapacityType().name(),
            trip.getMaxWeightKg(),
            trip.getNotes(),
            trip.getStatus().name(),
            trip.getCreatedAt(),
            trip.getUpdatedAt(),
            includeTraveler ? TravelerInfoDto.from(trip.getUser()) : null,
            (int) totalRequests,
            (int) pendingRequests
        );
    }
}
```

### 3.3 ItemRequest DTOs

**File:** `src/main/java/com/habeshago/request/dto/ItemRequestDto.java`

```java
public record ItemRequestDto(
    String id,
    String tripId,
    String senderUserId,
    String description,
    Double weightKg,
    String specialInstructions,
    String pickupPhotoUrl,
    String deliveryPhotoUrl,
    String status,
    Boolean paid,
    Instant createdAt,
    Instant updatedAt,
    // Embedded objects
    TripDto trip,
    SenderInfoDto sender
) {
    public static ItemRequestDto from(ItemRequest request) {
        return from(request, false, true);
    }
    
    public static ItemRequestDto from(ItemRequest request, boolean includeTrip, boolean includeSender) {
        return new ItemRequestDto(
            request.getId().toString(),
            request.getTrip().getId().toString(),
            request.getSenderUser().getId().toString(),
            request.getDescription(),
            request.getWeightKg(),
            request.getSpecialInstructions(),
            request.getPickupPhotoUrl(),
            request.getDeliveryPhotoUrl(),
            request.getStatus().name(),
            request.getPaid(),
            request.getCreatedAt(),
            request.getUpdatedAt(),
            includeTrip ? TripDto.from(request.getTrip()) : null,
            includeSender ? SenderInfoDto.from(request.getSenderUser()) : null
        );
    }
}
```

### 3.4 Review DTOs

**File:** `src/main/java/com/habeshago/review/dto/CreateReviewRequest.java`

```java
public record CreateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 2000) String comment
) {}
```

**File:** `src/main/java/com/habeshago/review/dto/ReviewDto.java`

```java
public record ReviewDto(
    String id,
    String tripId,
    String itemRequestId,
    Integer rating,
    String comment,
    Instant createdAt,
    // Trip summary for context
    String fromCity,
    String toCity,
    LocalDate departureDate
) {
    public static ReviewDto from(Review review) {
        Trip trip = review.getTrip();
        return new ReviewDto(
            review.getId().toString(),
            trip.getId().toString(),
            review.getItemRequest().getId().toString(),
            review.getRating(),
            review.getComment(),
            review.getCreatedAt(),
            trip.getFromCity(),
            trip.getToCity(),
            trip.getDepartureDate()
        );
    }
}
```

**File:** `src/main/java/com/habeshago/user/dto/TravelerProfileDto.java`

```java
public record TravelerProfileDto(
    String userId,
    String firstName,
    String lastName,
    String username,
    Boolean verified,
    Instant verifiedAt,
    Double ratingAverage,
    Integer ratingCount,
    Integer completedTripsCount,
    Integer completedDeliveriesCount,
    List<ReviewDto> recentReviews
) {
    public static TravelerProfileDto from(User user, List<Review> recentReviews) {
        return new TravelerProfileDto(
            user.getId().toString(),
            user.getFirstName(),
            user.getLastName(),
            user.getUsername(),
            user.getVerified(),
            user.getVerifiedAt(),
            user.getRatingAverage(),
            user.getRatingCount(),
            user.getCompletedTripsCount(),
            user.getCompletedDeliveriesCount(),
            recentReviews.stream().map(ReviewDto::from).toList()
        );
    }
}
```

---

## PART 4: Repositories

### 4.1 Review Repository

**File:** `src/main/java/com/habeshago/review/ReviewRepository.java`

```java
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    Optional<Review> findByItemRequestIdAndReviewerId(Long itemRequestId, Long reviewerId);
    
    List<Review> findByReviewedTravelerIdOrderByCreatedAtDesc(Long travelerId, Pageable pageable);
    
    List<Review> findTop5ByReviewedTravelerIdOrderByCreatedAtDesc(Long travelerId);
    
    boolean existsByItemRequestIdAndReviewerId(Long itemRequestId, Long reviewerId);
}
```

### 4.2 Update ItemRequest Repository

**File:** `src/main/java/com/habeshago/request/ItemRequestRepository.java`

Add method to find by ID:

```java
@Repository
public interface ItemRequestRepository extends JpaRepository<ItemRequest, Long> {
    
    List<ItemRequest> findByTripId(Long tripId);
    
    List<ItemRequest> findBySenderUserIdOrderByCreatedAtDesc(Long senderUserId);
    
    List<ItemRequest> findByTripIdAndStatus(Long tripId, RequestStatus status);
    
    // Count delivered requests for a traveler (for reputation)
    @Query("SELECT COUNT(r) FROM ItemRequest r WHERE r.trip.user.id = :travelerId AND r.status = 'DELIVERED'")
    long countDeliveredByTravelerId(@Param("travelerId") Long travelerId);
}
```

---

## PART 5: Services

### 5.1 Review Service

**File:** `src/main/java/com/habeshago/review/ReviewService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {
    
    private final ReviewRepository reviewRepository;
    private final ItemRequestRepository itemRequestRepository;
    private final UserRepository userRepository;
    
    public ReviewDto createReview(Long requestId, Long reviewerId, CreateReviewRequest request) {
        ItemRequest itemRequest = itemRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found"));
        
        // Validate: Only sender can review
        if (!itemRequest.getSenderUser().getId().equals(reviewerId)) {
            throw new ForbiddenException("Only the sender can review this request");
        }
        
        // Validate: Only DELIVERED requests can be reviewed
        if (itemRequest.getStatus() != RequestStatus.DELIVERED) {
            throw new BadRequestException("Can only review delivered requests");
        }
        
        // Check if review already exists
        if (reviewRepository.existsByItemRequestIdAndReviewerId(requestId, reviewerId)) {
            throw new ConflictException("You have already reviewed this request");
        }
        
        User reviewer = userRepository.findById(reviewerId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        User traveler = itemRequest.getTrip().getUser();
        
        // Create review
        Review review = new Review();
        review.setTrip(itemRequest.getTrip());
        review.setItemRequest(itemRequest);
        review.setReviewer(reviewer);
        review.setReviewedTraveler(traveler);
        review.setRating(request.rating());
        review.setComment(request.comment());
        
        review = reviewRepository.save(review);
        
        // Update traveler's rating
        updateTravelerRating(traveler, request.rating());
        
        return ReviewDto.from(review);
    }
    
    private void updateTravelerRating(User traveler, int newRating) {
        int oldCount = traveler.getRatingCount();
        Double oldAverage = traveler.getRatingAverage();
        
        int newCount = oldCount + 1;
        double newAverage;
        
        if (oldAverage == null || oldCount == 0) {
            newAverage = newRating;
        } else {
            newAverage = ((oldAverage * oldCount) + newRating) / newCount;
        }
        
        traveler.setRatingCount(newCount);
        traveler.setRatingAverage(newAverage);
        userRepository.save(traveler);
    }
    
    @Transactional(readOnly = true)
    public List<ReviewDto> getTravelerReviews(Long travelerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return reviewRepository.findByReviewedTravelerIdOrderByCreatedAtDesc(travelerId, pageable)
            .stream()
            .map(ReviewDto::from)
            .toList();
    }
}
```

### 5.2 Traveler Profile Service

**File:** `src/main/java/com/habeshago/user/TravelerService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelerService {
    
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    
    public TravelerProfileDto getTravelerProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        List<Review> recentReviews = reviewRepository
            .findTop5ByReviewedTravelerIdOrderByCreatedAtDesc(userId);
        
        return TravelerProfileDto.from(user, recentReviews);
    }
}
```

### 5.3 Update ItemRequest Service - Add Mark Delivered

**File:** `src/main/java/com/habeshago/request/ItemRequestService.java`

Add these methods:

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ItemRequestService {
    
    private final ItemRequestRepository itemRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    
    // ... existing methods ...
    
    public ItemRequestDto getRequestById(Long requestId, Long currentUserId) {
        ItemRequest request = itemRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found"));
        
        // Check access: must be sender or traveler
        boolean isSender = request.getSenderUser().getId().equals(currentUserId);
        boolean isTraveler = request.getTrip().getUser().getId().equals(currentUserId);
        
        if (!isSender && !isTraveler) {
            throw new ForbiddenException("Access denied");
        }
        
        return ItemRequestDto.from(request, true, true);
    }
    
    public ItemRequestDto markAsDelivered(Long requestId, Long travelerId) {
        ItemRequest request = itemRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found"));
        
        // Validate: Only traveler can mark as delivered
        if (!request.getTrip().getUser().getId().equals(travelerId)) {
            throw new ForbiddenException("Only the traveler can mark as delivered");
        }
        
        // Validate: Must be ACCEPTED status
        if (request.getStatus() != RequestStatus.ACCEPTED) {
            throw new BadRequestException("Can only mark accepted requests as delivered");
        }
        
        request.setStatus(RequestStatus.DELIVERED);
        request = itemRequestRepository.save(request);
        
        // Update traveler's delivery count
        User traveler = request.getTrip().getUser();
        traveler.setCompletedDeliveriesCount(traveler.getCompletedDeliveriesCount() + 1);
        userRepository.save(traveler);
        
        // Notify sender
        notificationService.sendRequestDeliveredNotification(request);
        
        return ItemRequestDto.from(request, true, true);
    }
    
    public ItemRequestDto cancelRequest(Long requestId, Long userId) {
        ItemRequest request = itemRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found"));
        
        // Only sender can cancel, and only if PENDING
        if (!request.getSenderUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the sender can cancel");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Can only cancel pending requests");
        }
        
        request.setStatus(RequestStatus.CANCELLED);
        request = itemRequestRepository.save(request);
        
        return ItemRequestDto.from(request, true, true);
    }
    
    public ItemRequestDto acceptRequest(Long requestId, Long travelerId) {
        ItemRequest request = itemRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found"));
        
        // Validate: Only traveler can accept
        if (!request.getTrip().getUser().getId().equals(travelerId)) {
            throw new ForbiddenException("Only the traveler can accept requests");
        }
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Can only accept pending requests");
        }
        
        request.setStatus(RequestStatus.ACCEPTED);
        request = itemRequestRepository.save(request);
        
        // Send notification to sender with traveler contact info
        notificationService.sendRequestAcceptedNotification(request);
        
        // Send notification to traveler with sender contact info
        notificationService.sendRequestAcceptedTravelerNotification(request);
        
        return ItemRequestDto.from(request, true, true);
    }
}
```

---

## PART 6: Notification Service - User Communication

This is the KEY part for enabling user-to-user communication via Telegram.

**File:** `src/main/java/com/habeshago/notification/NotificationService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationOutboxRepository outboxRepository;
    
    /**
     * Called when traveler ACCEPTS a request.
     * Sends notification to SENDER with traveler's contact info.
     */
    public void sendRequestAcceptedNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        User traveler = request.getTrip().getUser();
        Trip trip = request.getTrip();
        
        // Build message for sender
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_ACCEPTED");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        
        // Message content
        payload.put("title", "✅ Your request was accepted!");
        payload.put("itemDescription", request.getDescription());
        payload.put("route", trip.getFromCity() + " → " + trip.getToCity());
        payload.put("departureDate", trip.getDepartureDate().toString());
        
        // Traveler contact info
        payload.put("travelerFirstName", traveler.getFirstName());
        payload.put("travelerLastName", traveler.getLastName());
        payload.put("travelerUsername", traveler.getUsername());
        payload.put("travelerVerified", traveler.getVerified());
        payload.put("travelerRating", traveler.getRatingAverage());
        
        // Include contact button URL
        if (traveler.getUsername() != null) {
            payload.put("contactUrl", "https://t.me/" + traveler.getUsername());
            payload.put("contactButtonText", "💬 Message " + traveler.getFirstName());
        }
        
        createOutboxEntry(sender, NotificationType.REQUEST_ACCEPTED, payload);
    }
    
    /**
     * Called when traveler ACCEPTS a request.
     * Sends notification to TRAVELER with sender's contact info.
     */
    public void sendRequestAcceptedTravelerNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        User traveler = request.getTrip().getUser();
        Trip trip = request.getTrip();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_ACCEPTED_TRAVELER");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        
        // Message content
        payload.put("title", "📦 You accepted a new request!");
        payload.put("itemDescription", request.getDescription());
        payload.put("itemWeight", request.getWeightKg());
        payload.put("specialInstructions", request.getSpecialInstructions());
        payload.put("route", trip.getFromCity() + " → " + trip.getToCity());
        
        // Sender contact info
        payload.put("senderFirstName", sender.getFirstName());
        payload.put("senderLastName", sender.getLastName());
        payload.put("senderUsername", sender.getUsername());
        
        // Include contact button URL
        if (sender.getUsername() != null) {
            payload.put("contactUrl", "https://t.me/" + sender.getUsername());
            payload.put("contactButtonText", "💬 Message " + sender.getFirstName());
        }
        
        createOutboxEntry(traveler, NotificationType.REQUEST_ACCEPTED_TRAVELER, payload);
    }
    
    /**
     * Called when traveler marks request as DELIVERED.
     * Notifies sender and prompts for review.
     */
    public void sendRequestDeliveredNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        User traveler = request.getTrip().getUser();
        Trip trip = request.getTrip();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_DELIVERED");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        
        payload.put("title", "🎉 Your item was delivered!");
        payload.put("itemDescription", request.getDescription());
        payload.put("route", trip.getFromCity() + " → " + trip.getToCity());
        payload.put("travelerFirstName", traveler.getFirstName());
        
        // Prompt to leave review
        payload.put("reviewPrompt", "How was your experience with " + traveler.getFirstName() + "?");
        
        createOutboxEntry(sender, NotificationType.REQUEST_DELIVERED, payload);
    }
    
    /**
     * Called when traveler REJECTS a request.
     */
    public void sendRequestRejectedNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        Trip trip = request.getTrip();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_REJECTED");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        
        payload.put("title", "❌ Request not accepted");
        payload.put("itemDescription", request.getDescription());
        payload.put("route", trip.getFromCity() + " → " + trip.getToCity());
        payload.put("message", "The traveler was unable to accept your request. You can search for other travelers on this route.");
        
        createOutboxEntry(sender, NotificationType.REQUEST_REJECTED, payload);
    }
    
    /**
     * Called when a new request is created.
     * Notifies the traveler.
     */
    public void sendNewRequestNotification(ItemRequest request) {
        User traveler = request.getTrip().getUser();
        User sender = request.getSenderUser();
        Trip trip = request.getTrip();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_REQUEST");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        
        payload.put("title", "📬 New item request!");
        payload.put("itemDescription", request.getDescription());
        payload.put("itemWeight", request.getWeightKg());
        payload.put("route", trip.getFromCity() + " → " + trip.getToCity());
        payload.put("departureDate", trip.getDepartureDate().toString());
        payload.put("senderFirstName", sender.getFirstName());
        
        createOutboxEntry(traveler, NotificationType.NEW_REQUEST, payload);
    }
    
    private void createOutboxEntry(User user, NotificationType type, Map<String, Object> payload) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUser(user);
        outbox.setType(type);
        outbox.setPayload(toJson(payload));
        outbox.setStatus(NotificationStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setNextAttemptAt(Instant.now());
        
        outboxRepository.save(outbox);
        log.info("Created notification outbox entry: type={}, userId={}", type, user.getId());
    }
    
    private String toJson(Map<String, Object> payload) {
        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }
}
```

### 6.1 Notification Type Enum

**File:** `src/main/java/com/habeshago/notification/NotificationType.java`

```java
public enum NotificationType {
    NEW_REQUEST,
    REQUEST_ACCEPTED,
    REQUEST_ACCEPTED_TRAVELER,
    REQUEST_REJECTED,
    REQUEST_DELIVERED,
    REQUEST_CANCELLED,
    TRIP_REMINDER,
    REVIEW_PROMPT
}
```

---

## PART 7: Telegram Bot Message Formatter

**File:** `src/main/java/com/habeshago/telegram/TelegramMessageFormatter.java`

```java
@Component
public class TelegramMessageFormatter {
    
    public TelegramMessage formatNotification(NotificationOutbox notification) {
        Map<String, Object> payload = parsePayload(notification.getPayload());
        String type = (String) payload.get("type");
        
        return switch (type) {
            case "REQUEST_ACCEPTED" -> formatRequestAccepted(payload);
            case "REQUEST_ACCEPTED_TRAVELER" -> formatRequestAcceptedTraveler(payload);
            case "REQUEST_DELIVERED" -> formatRequestDelivered(payload);
            case "REQUEST_REJECTED" -> formatRequestRejected(payload);
            case "NEW_REQUEST" -> formatNewRequest(payload);
            default -> formatGeneric(payload);
        };
    }
    
    private TelegramMessage formatRequestAccepted(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("✅ *Your request was accepted!*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n");
        text.append("📅 ").append(payload.get("departureDate")).append("\n\n");
        
        // Traveler info
        text.append("🧳 *Traveler:* ").append(payload.get("travelerFirstName"));
        if (payload.get("travelerLastName") != null) {
            text.append(" ").append(((String) payload.get("travelerLastName")).charAt(0)).append(".");
        }
        
        Boolean verified = (Boolean) payload.get("travelerVerified");
        if (verified != null && verified) {
            text.append(" ✓");
        }
        
        Double rating = (Double) payload.get("travelerRating");
        if (rating != null) {
            text.append(String.format(" (%.1f⭐)", rating));
        }
        text.append("\n\n");
        
        text.append("_Tap below to coordinate pickup details:_");
        
        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");
        
        // Add inline keyboard with contact button
        String contactUrl = (String) payload.get("contactUrl");
        String buttonText = (String) payload.get("contactButtonText");
        if (contactUrl != null && buttonText != null) {
            message.setInlineKeyboard(List.of(
                List.of(new InlineKeyboardButton(buttonText, contactUrl))
            ));
        }
        
        return message;
    }
    
    private TelegramMessage formatRequestAcceptedTraveler(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("📦 *You accepted a new request!*\n\n");
        text.append("*Item:* ").append(payload.get("itemDescription")).append("\n");
        
        Object weight = payload.get("itemWeight");
        if (weight != null) {
            text.append("*Weight:* ").append(weight).append(" kg\n");
        }
        
        String instructions = (String) payload.get("specialInstructions");
        if (instructions != null && !instructions.isBlank()) {
            text.append("*Instructions:* ").append(instructions).append("\n");
        }
        
        text.append("\n");
        text.append("👤 *Sender:* ").append(payload.get("senderFirstName"));
        if (payload.get("senderLastName") != null) {
            text.append(" ").append(((String) payload.get("senderLastName")).charAt(0)).append(".");
        }
        text.append("\n\n");
        
        text.append("_Tap below to coordinate with sender:_");
        
        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");
        
        String contactUrl = (String) payload.get("contactUrl");
        String buttonText = (String) payload.get("contactButtonText");
        if (contactUrl != null && buttonText != null) {
            message.setInlineKeyboard(List.of(
                List.of(new InlineKeyboardButton(buttonText, contactUrl))
            ));
        }
        
        return message;
    }
    
    private TelegramMessage formatRequestDelivered(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("🎉 *Your item was delivered!*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n\n");
        text.append("Thanks to *").append(payload.get("travelerFirstName")).append("* for carrying your item!\n\n");
        text.append("_How was your experience? Leave a review in the app._");
        
        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");
        
        return message;
    }
    
    private TelegramMessage formatRequestRejected(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("❌ *Request not accepted*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n\n");
        text.append((String) payload.get("message"));
        
        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");
        
        return message;
    }
    
    private TelegramMessage formatNewRequest(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("📬 *New item request!*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        
        Object weight = payload.get("itemWeight");
        if (weight != null) {
            text.append("⚖️ ").append(weight).append(" kg\n");
        }
        
        text.append("✈️ ").append(payload.get("route")).append("\n");
        text.append("📅 ").append(payload.get("departureDate")).append("\n\n");
        text.append("👤 From: ").append(payload.get("senderFirstName")).append("\n\n");
        text.append("_Open the app to accept or decline._");
        
        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");
        
        return message;
    }
    
    private TelegramMessage formatGeneric(Map<String, Object> payload) {
        TelegramMessage message = new TelegramMessage();
        message.setText((String) payload.getOrDefault("title", "You have a new notification"));
        return message;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}

// Supporting classes
@Data
public class TelegramMessage {
    private String text;
    private String parseMode;
    private List<List<InlineKeyboardButton>> inlineKeyboard;
}

@Data
@AllArgsConstructor
public class InlineKeyboardButton {
    private String text;
    private String url;
}
```

---

## PART 8: REST Controllers

### 8.1 Add Missing Endpoints

**File:** `src/main/java/com/habeshago/request/ItemRequestController.java`

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ItemRequestController {
    
    private final ItemRequestService itemRequestService;
    private final AuthService authService;
    
    // GET single request by ID
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<ItemRequestDto> getRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
            itemRequestService.getRequestById(requestId, principal.getUserId())
        );
    }
    
    // Mark as delivered
    @PostMapping("/requests/{requestId}/delivered")
    public ResponseEntity<ItemRequestDto> markDelivered(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
            itemRequestService.markAsDelivered(requestId, principal.getUserId())
        );
    }
    
    // Cancel request (by sender)
    @PostMapping("/requests/{requestId}/cancel")
    public ResponseEntity<ItemRequestDto> cancelRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
            itemRequestService.cancelRequest(requestId, principal.getUserId())
        );
    }
    
    // Existing endpoints...
    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ItemRequestDto> acceptRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
            itemRequestService.acceptRequest(requestId, principal.getUserId())
        );
    }
    
    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ItemRequestDto> rejectRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
            itemRequestService.rejectRequest(requestId, principal.getUserId())
        );
    }
}
```

### 8.2 Review Controller

**File:** `src/main/java/com/habeshago/review/ReviewController.java`

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
    
    private final ReviewService reviewService;
    
    @PostMapping("/requests/{requestId}/review")
    public ResponseEntity<ReviewDto> createReview(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            reviewService.createReview(requestId, principal.getUserId(), request)
        );
    }
    
    @GetMapping("/travelers/{userId}/reviews")
    public ResponseEntity<List<ReviewDto>> getTravelerReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
            reviewService.getTravelerReviews(userId, page, size)
        );
    }
}
```

### 8.3 Traveler Profile Controller

**File:** `src/main/java/com/habeshago/user/TravelerController.java`

```java
@RestController
@RequestMapping("/api/travelers")
@RequiredArgsConstructor
public class TravelerController {
    
    private final TravelerService travelerService;
    
    @GetMapping("/{userId}/profile")
    public ResponseEntity<TravelerProfileDto> getTravelerProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(travelerService.getTravelerProfile(userId));
    }
}
```

### 8.4 Trip Controller - Add Cancel

**File:** `src/main/java/com/habeshago/trip/TripController.java`

Add cancel endpoint:

```java
@PostMapping("/trips/{tripId}/cancel")
public ResponseEntity<TripDto> cancelTrip(
        @PathVariable Long tripId,
        @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        tripService.cancelTrip(tripId, principal.getUserId())
    );
}
```

---

## PART 9: Complete API Endpoint Summary

### Authentication
```
POST /api/auth/telegram          - Login with Telegram initData
```

### User
```
GET  /api/me                     - Get current user
PUT  /api/me/language            - Update language preference
```

### Trips
```
POST /api/trips                  - Create trip
GET  /api/trips/my               - Get my trips
GET  /api/trips/{id}             - Get trip by ID
POST /api/trips/search           - Search trips
POST /api/trips/{id}/cancel      - Cancel trip (NEW)
GET  /api/trips/{id}/requests    - Get requests for trip
```

### Requests
```
POST /api/trips/{tripId}/requests    - Create request
GET  /api/requests/my                - Get my requests
GET  /api/requests/{id}              - Get request by ID (NEW)
POST /api/requests/{id}/accept       - Accept request
POST /api/requests/{id}/reject       - Reject request
POST /api/requests/{id}/cancel       - Cancel request (NEW)
POST /api/requests/{id}/delivered    - Mark as delivered (NEW)
POST /api/requests/{id}/review       - Create review (NEW)
```

### Travelers
```
GET  /api/travelers/{userId}/profile  - Get traveler profile (NEW)
GET  /api/travelers/{userId}/reviews  - Get traveler reviews (NEW)
```

---

## PART 10: Summary of Changes

### New Entities
1. `Review` - For traveler reviews/ratings

### Updated Entities
1. `User` - Added reputation fields (rating, counts, verified)
2. `Trip` - Added COMPLETED status
3. `ItemRequest` - Added DELIVERED status, paid flag

### New Services
1. `ReviewService` - Handle review creation and retrieval
2. `TravelerService` - Get traveler profiles

### Updated Services
1. `ItemRequestService` - Added markAsDelivered, cancelRequest, getById
2. `NotificationService` - Added all notification methods with contact info

### New Endpoints
1. `GET /api/requests/{id}` - Get single request
2. `POST /api/requests/{id}/cancel` - Cancel request
3. `POST /api/requests/{id}/delivered` - Mark delivered
4. `POST /api/requests/{id}/review` - Create review
5. `GET /api/travelers/{userId}/profile` - Traveler profile
6. `GET /api/travelers/{userId}/reviews` - Traveler reviews
7. `POST /api/trips/{id}/cancel` - Cancel trip

### Database Migrations
1. `V2__add_user_reputation_fields.sql`
2. `V3__add_delivery_status.sql`
3. `V4__create_reviews_table.sql`

### Key Feature: User Communication
- When request is ACCEPTED, both sender and traveler receive Telegram bot messages
- Messages include the other party's name and @username
- Messages have inline keyboard button to open Telegram chat directly
- This enables users to coordinate pickup/delivery via standard Telegram messaging
