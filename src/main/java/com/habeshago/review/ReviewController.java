package com.habeshago.review;

import com.habeshago.auth.AuthInterceptor;
import com.habeshago.review.dto.CreateReviewRequest;
import com.habeshago.review.dto.ReviewDto;
import com.habeshago.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    private User requireCurrentUser(HttpServletRequest request) {
        User user = AuthInterceptor.getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return user;
    }

    @PostMapping("/requests/{requestId}/reviews")
    public ResponseEntity<ReviewDto> createReview(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @Valid @RequestBody CreateReviewRequest body) {
        User user = requireCurrentUser(request);
        ReviewDto review = reviewService.createReview(requestId, user.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    @GetMapping("/requests/{requestId}/reviews")
    public ResponseEntity<List<ReviewDto>> getReviewsByRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(reviewService.getReviewsByRequest(requestId));
    }

    @GetMapping("/travelers/{userId}/reviews")
    public ResponseEntity<List<ReviewDto>> getTravelerReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.getTravelerReviews(userId, page, size));
    }
}
