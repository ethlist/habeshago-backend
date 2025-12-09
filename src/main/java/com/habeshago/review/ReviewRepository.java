package com.habeshago.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByItemRequestIdAndReviewerId(Long itemRequestId, Long reviewerId);

    List<Review> findByReviewedTravelerIdOrderByCreatedAtDesc(Long travelerId, Pageable pageable);

    List<Review> findTop5ByReviewedTravelerIdOrderByCreatedAtDesc(Long travelerId);

    boolean existsByItemRequestIdAndReviewerId(Long itemRequestId, Long reviewerId);

    List<Review> findByItemRequestId(Long itemRequestId);
}
