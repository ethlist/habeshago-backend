package com.habeshago.request;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.request.dto.ItemRequestCreateRequest;
import com.habeshago.request.dto.ItemRequestDto;
import com.habeshago.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ItemRequestController {

    private final ItemRequestService itemRequestService;

    public ItemRequestController(ItemRequestService itemRequestService) {
        this.itemRequestService = itemRequestService;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    @PostMapping("/trips/{tripId}/requests")
    public ResponseEntity<ItemRequestDto> createRequest(
            HttpServletRequest request,
            @PathVariable Long tripId,
            @Valid @RequestBody ItemRequestCreateRequest body) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.createRequest(user, tripId, body));
    }

    @GetMapping("/trips/{tripId}/requests")
    public ResponseEntity<List<ItemRequestDto>> getRequestsForTrip(
            HttpServletRequest request,
            @PathVariable Long tripId) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.getRequestsForTrip(user, tripId));
    }

    @GetMapping("/requests/my")
    public ResponseEntity<List<ItemRequestDto>> getMyRequests(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.getMyRequests(user));
    }

    // GET single request by ID
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<ItemRequestDto> getRequest(
            HttpServletRequest request,
            @PathVariable Long requestId) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.getRequestById(requestId, user.getId()));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ItemRequestDto> acceptRequest(
            HttpServletRequest request,
            @PathVariable Long requestId) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.acceptRequest(user, requestId));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ItemRequestDto> rejectRequest(
            HttpServletRequest request,
            @PathVariable Long requestId) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.rejectRequest(user, requestId));
    }

    // Cancel request (by sender)
    @PostMapping("/requests/{requestId}/cancel")
    public ResponseEntity<ItemRequestDto> cancelRequest(
            HttpServletRequest request,
            @PathVariable Long requestId) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.cancelRequest(requestId, user.getId()));
    }

    // Mark as delivered (by traveler)
    @PostMapping("/requests/{requestId}/delivered")
    public ResponseEntity<ItemRequestDto> markDelivered(
            HttpServletRequest request,
            @PathVariable Long requestId) {
        User user = requireCurrentUser(request);
        return ResponseEntity.ok(itemRequestService.markAsDelivered(requestId, user.getId()));
    }
}
