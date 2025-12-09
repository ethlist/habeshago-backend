package com.habeshago.user;

import com.habeshago.common.NotFoundException;
import com.habeshago.review.Review;
import com.habeshago.review.ReviewRepository;
import com.habeshago.user.dto.TravelerProfileDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TravelerService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    public TravelerService(UserRepository userRepository, ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    public TravelerProfileDto getTravelerProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<Review> recentReviews = reviewRepository
                .findTop5ByReviewedTravelerIdOrderByCreatedAtDesc(userId);

        return TravelerProfileDto.from(user, recentReviews);
    }
}
