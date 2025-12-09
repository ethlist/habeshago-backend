package com.habeshago.review;

import com.habeshago.common.BadRequestException;
import com.habeshago.common.ConflictException;
import com.habeshago.common.ForbiddenException;
import com.habeshago.common.NotFoundException;
import com.habeshago.request.ItemRequest;
import com.habeshago.request.ItemRequestRepository;
import com.habeshago.request.RequestStatus;
import com.habeshago.review.dto.CreateReviewRequest;
import com.habeshago.review.dto.ReviewDto;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ItemRequestRepository itemRequestRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            ItemRequestRepository itemRequestRepository,
            UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.itemRequestRepository = itemRequestRepository;
        this.userRepository = userRepository;
    }

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

        // Check if review already exists (optimistic check)
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

        try {
            review = reviewRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            // Handle race condition: another request created the review concurrently
            log.warn("Duplicate review attempt for request {} by user {} - caught by DB constraint",
                    requestId, reviewerId);
            throw new ConflictException("You have already reviewed this request");
        }

        // Update traveler's rating
        updateTravelerRating(traveler, request.rating());

        return ReviewDto.from(review);
    }

    /**
     * Update traveler rating using atomic database operation to prevent race conditions.
     * This ensures consistent results when multiple reviews are submitted concurrently.
     */
    private void updateTravelerRating(User traveler, int newRating) {
        userRepository.updateRatingAtomically(traveler.getId(), newRating);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getTravelerReviews(Long travelerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return reviewRepository.findByReviewedTravelerIdOrderByCreatedAtDesc(travelerId, pageable)
                .stream()
                .map(ReviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getRecentTravelerReviews(Long travelerId) {
        return reviewRepository.findTop5ByReviewedTravelerIdOrderByCreatedAtDesc(travelerId)
                .stream()
                .map(ReviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getReviewsByRequest(Long requestId) {
        return reviewRepository.findByItemRequestId(requestId)
                .stream()
                .map(ReviewDto::from)
                .toList();
    }
}
