package com.habeshago.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshago.common.BadRequestException;
import com.habeshago.common.ForbiddenException;
import com.habeshago.common.NotFoundException;
import com.habeshago.notification.Notification;
import com.habeshago.notification.NotificationOutbox;
import com.habeshago.notification.NotificationRepository;
import com.habeshago.notification.NotificationService;
import com.habeshago.trip.ContactMethod;
import com.habeshago.trip.Trip;
import com.habeshago.trip.TripRepository;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import com.habeshago.request.dto.ItemRequestCreateRequest;
import com.habeshago.request.dto.ItemRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class ItemRequestService {

    private final ItemRequestRepository itemRequestRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ItemRequestService(ItemRequestRepository itemRequestRepository,
                              TripRepository tripRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              NotificationRepository notificationRepository) {
        this.itemRequestRepository = itemRequestRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public ItemRequestDto createRequest(User sender, Long tripId, ItemRequestCreateRequest req) {
        // Validate user has at least one contact method enabled
        validateHasContactMethod(sender);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        if (trip.getUser().getId().equals(sender.getId())) {
            throw new BadRequestException("Cannot request your own trip");
        }

        ItemRequest ir = new ItemRequest();
        ir.setTrip(trip);
        ir.setSenderUser(sender);
        ir.setDescription(req.getDescription());
        ir.setWeightKg(req.getWeightKg());
        ir.setSpecialInstructions(req.getSpecialInstructions());
        ir.setStatus(RequestStatus.PENDING);

        // Set new multiple contact fields from sender's enabled contact methods
        if (Boolean.TRUE.equals(sender.getContactTelegramEnabled()) && sender.getUsername() != null) {
            ir.setSenderContactTelegram(sender.getUsername());
        }
        if (Boolean.TRUE.equals(sender.getContactPhoneEnabled()) && sender.getPhoneNumber() != null) {
            ir.setSenderContactPhone(sender.getPhoneNumber());
        }

        // Also set legacy fields for backward compatibility
        if (req.getContactMethod() != null) {
            ir.setSenderContactMethod(req.getContactMethod());
            ir.setSenderContactValue(resolveContactValue(sender, req.getContactMethod()));
        } else {
            // Default to first available contact method for legacy support
            if (ir.getSenderContactTelegram() != null) {
                ir.setSenderContactMethod(ContactMethod.TELEGRAM);
                ir.setSenderContactValue(ir.getSenderContactTelegram());
            } else if (ir.getSenderContactPhone() != null) {
                ir.setSenderContactMethod(ContactMethod.PHONE);
                ir.setSenderContactValue(ir.getSenderContactPhone());
            }
        }

        ItemRequest saved = itemRequestRepository.save(ir);

        // Enqueue notification for traveler
        sendNewRequestNotification(saved);

        return ItemRequestDto.from(saved);
    }

    /**
     * Validates that a user has at least one contact method enabled.
     * Uses the new contact method preference system.
     */
    private void validateHasContactMethod(User user) {
        if (!user.hasAtLeastOneContactMethod()) {
            throw new BadRequestException(
                "You need at least one contact method to send requests. " +
                "Please enable Telegram or Phone in your contact settings."
            );
        }
    }

    /**
     * Resolves the actual contact value based on the chosen contact method.
     */
    private String resolveContactValue(User user, ContactMethod contactMethod) {
        if (contactMethod == ContactMethod.TELEGRAM) {
            String username = user.getUsername();
            if (username == null || username.isBlank()) {
                throw new BadRequestException(
                    "Cannot use Telegram as contact method - you don't have a Telegram username. " +
                    "Please select Phone instead or set a username in Telegram."
                );
            }
            return username;
        } else if (contactMethod == ContactMethod.PHONE) {
            if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
                throw new BadRequestException(
                    "Cannot use Phone as contact method - phone not verified. " +
                    "Please verify your phone number first."
                );
            }
            return user.getPhoneNumber();
        }
        throw new BadRequestException("Invalid contact method");
    }

    @Transactional(readOnly = true)
    public ItemRequestDto getRequestById(Long requestId, Long currentUserId) {
        ItemRequest request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found"));

        // Check access: must be sender or traveler
        boolean isSender = request.getSenderUser().getId().equals(currentUserId);
        boolean isTraveler = request.getTrip().getUser().getId().equals(currentUserId);

        if (!isSender && !isTraveler) {
            throw new ForbiddenException("Access denied");
        }

        return ItemRequestDto.from(request);
    }

    @Transactional(readOnly = true)
    public List<ItemRequestDto> getRequestsForTrip(User currentUser, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        if (!trip.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Not owner of this trip");
        }
        return itemRequestRepository.findByTrip(trip).stream()
                .map(ItemRequestDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemRequestDto> getMyRequests(User currentUser) {
        return itemRequestRepository.findBySenderUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(ItemRequestDto::from)
                .toList();
    }

    @Transactional
    public ItemRequestDto acceptRequest(User currentUser, Long requestId) {
        ItemRequest ir = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found"));

        if (!ir.getTrip().getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the traveler can accept requests");
        }

        if (ir.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Can only accept pending requests");
        }

        ir.setStatus(RequestStatus.ACCEPTED);
        ir.setContactRevealedAt(Instant.now());
        itemRequestRepository.save(ir);

        // Increment traveler's accepted requests count (for completion rate tracking)
        User traveler = ir.getTrip().getUser();
        traveler.setAcceptedRequestsCount(
                (traveler.getAcceptedRequestsCount() != null ? traveler.getAcceptedRequestsCount() : 0) + 1
        );
        userRepository.save(traveler);

        // Send notification to sender with traveler contact info
        sendRequestAcceptedNotification(ir);

        // Send notification to traveler with sender contact info
        sendRequestAcceptedTravelerNotification(ir);

        return ItemRequestDto.from(ir);
    }

    @Transactional
    public ItemRequestDto rejectRequest(User currentUser, Long requestId) {
        ItemRequest ir = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found"));

        if (!ir.getTrip().getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the traveler can reject requests");
        }

        if (ir.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Can only reject pending requests");
        }

        ir.setStatus(RequestStatus.REJECTED);
        itemRequestRepository.save(ir);

        // Send notification to sender
        sendRequestRejectedNotification(ir);

        return ItemRequestDto.from(ir);
    }

    @Transactional
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
        itemRequestRepository.save(request);

        // Update traveler's delivery count
        User traveler = request.getTrip().getUser();
        traveler.setCompletedDeliveriesCount(traveler.getCompletedDeliveriesCount() + 1);
        userRepository.save(traveler);

        // Notify sender
        sendRequestDeliveredNotification(request);

        return ItemRequestDto.from(request);
    }

    @Transactional
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
        itemRequestRepository.save(request);

        return ItemRequestDto.from(request);
    }

    // Notification helpers
    private void sendNewRequestNotification(ItemRequest request) {
        User traveler = request.getTrip().getUser();
        User sender = request.getSenderUser();
        Trip trip = request.getTrip();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_REQUEST");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        payload.put("title", "New item request!");
        payload.put("itemDescription", request.getDescription());
        payload.put("itemWeight", request.getWeightKg());
        payload.put("route", trip.getFromCity() + " -> " + trip.getToCity());
        payload.put("departureDate", trip.getDepartureDate().toString());
        payload.put("senderFirstName", sender.getFirstName());

        enqueueNotification(traveler, "NEW_REQUEST", payload);
    }

    private void sendRequestAcceptedNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        User traveler = request.getTrip().getUser();
        Trip trip = request.getTrip();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_ACCEPTED");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        payload.put("title", "Your request was accepted!");
        payload.put("itemDescription", request.getDescription());
        payload.put("route", trip.getFromCity() + " -> " + trip.getToCity());
        payload.put("departureDate", trip.getDepartureDate().toString());
        payload.put("travelerFirstName", traveler.getFirstName());
        payload.put("travelerLastName", traveler.getLastName());
        payload.put("travelerUsername", traveler.getUsername());
        payload.put("travelerVerified", traveler.getVerified());
        payload.put("travelerRating", traveler.getRatingAverage());

        if (traveler.getUsername() != null) {
            payload.put("contactUrl", "https://t.me/" + traveler.getUsername());
            payload.put("contactButtonText", "Message " + traveler.getFirstName());
        }

        enqueueNotification(sender, "REQUEST_ACCEPTED", payload);
    }

    private void sendRequestAcceptedTravelerNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        User traveler = request.getTrip().getUser();
        Trip trip = request.getTrip();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_ACCEPTED_TRAVELER");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        payload.put("title", "You accepted a new request!");
        payload.put("itemDescription", request.getDescription());
        payload.put("itemWeight", request.getWeightKg());
        payload.put("specialInstructions", request.getSpecialInstructions());
        payload.put("route", trip.getFromCity() + " -> " + trip.getToCity());
        payload.put("senderFirstName", sender.getFirstName());
        payload.put("senderLastName", sender.getLastName());
        payload.put("senderUsername", sender.getUsername());

        if (sender.getUsername() != null) {
            payload.put("contactUrl", "https://t.me/" + sender.getUsername());
            payload.put("contactButtonText", "Message " + sender.getFirstName());
        }

        enqueueNotification(traveler, "REQUEST_ACCEPTED_TRAVELER", payload);
    }

    private void sendRequestRejectedNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        Trip trip = request.getTrip();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_REJECTED");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        payload.put("title", "Request not accepted");
        payload.put("itemDescription", request.getDescription());
        payload.put("route", trip.getFromCity() + " -> " + trip.getToCity());
        payload.put("message", "The traveler was unable to accept your request. You can search for other travelers on this route.");

        enqueueNotification(sender, "REQUEST_REJECTED", payload);
    }

    private void sendRequestDeliveredNotification(ItemRequest request) {
        User sender = request.getSenderUser();
        User traveler = request.getTrip().getUser();
        Trip trip = request.getTrip();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "REQUEST_DELIVERED");
        payload.put("requestId", request.getId());
        payload.put("tripId", trip.getId());
        payload.put("title", "Your item was delivered!");
        payload.put("itemDescription", request.getDescription());
        payload.put("route", trip.getFromCity() + " -> " + trip.getToCity());
        payload.put("travelerFirstName", traveler.getFirstName());
        payload.put("reviewPrompt", "How was your experience with " + traveler.getFirstName() + "?");

        enqueueNotification(sender, "REQUEST_DELIVERED", payload);
    }

    private void enqueueNotification(User user, String type, Map<String, Object> payload) {
        // 1. Create in-app notification (for all users)
        createInAppNotification(user, type, payload);

        // 2. Enqueue Telegram notification (will be sent if user has Telegram ID)
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUser(user);
        outbox.setType(type);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            outbox.setPayload("{}");
        }
        notificationService.enqueueNotification(outbox);
    }

    private void createInAppNotification(User user, String type, Map<String, Object> payload) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);

        // Extract title and message from payload
        String title = (String) payload.getOrDefault("title", getDefaultTitle(type));
        notification.setTitle(title);

        // Build message from payload
        String message = buildNotificationMessage(type, payload);
        notification.setMessage(message);

        // Build action URL based on notification type
        String actionUrl = buildActionUrl(type, payload);
        notification.setActionUrl(actionUrl);

        notificationRepository.save(notification);
    }

    private String getDefaultTitle(String type) {
        return switch (type) {
            case "NEW_REQUEST" -> "New item request";
            case "REQUEST_ACCEPTED" -> "Request accepted";
            case "REQUEST_ACCEPTED_TRAVELER" -> "You accepted a request";
            case "REQUEST_REJECTED" -> "Request not accepted";
            case "REQUEST_DELIVERED" -> "Item delivered";
            default -> "Notification";
        };
    }

    private String buildNotificationMessage(String type, Map<String, Object> payload) {
        String itemDesc = (String) payload.get("itemDescription");
        String route = (String) payload.get("route");

        return switch (type) {
            case "NEW_REQUEST" -> {
                String senderName = (String) payload.get("senderFirstName");
                yield senderName + " wants to send: " + itemDesc + " (" + route + ")";
            }
            case "REQUEST_ACCEPTED" -> {
                String travelerName = (String) payload.get("travelerFirstName");
                yield travelerName + " will carry your " + itemDesc + " (" + route + ")";
            }
            case "REQUEST_ACCEPTED_TRAVELER" -> {
                String senderName = (String) payload.get("senderFirstName");
                yield "You'll carry " + itemDesc + " for " + senderName;
            }
            case "REQUEST_REJECTED" ->
                "Your request for " + itemDesc + " was not accepted (" + route + ")";
            case "REQUEST_DELIVERED" -> {
                String travelerName = (String) payload.get("travelerFirstName");
                yield "Your " + itemDesc + " was delivered by " + travelerName;
            }
            default -> (String) payload.getOrDefault("message", "You have a notification");
        };
    }

    private String buildActionUrl(String type, Map<String, Object> payload) {
        Object requestId = payload.get("requestId");
        Object tripId = payload.get("tripId");

        return switch (type) {
            case "NEW_REQUEST" -> tripId != null ? "/trips/" + tripId : null;
            case "REQUEST_ACCEPTED", "REQUEST_ACCEPTED_TRAVELER", "REQUEST_REJECTED", "REQUEST_DELIVERED" ->
                requestId != null ? "/requests/" + requestId : null;
            default -> null;
        };
    }
}
