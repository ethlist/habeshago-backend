package com.habeshago.request;

import com.habeshago.trip.Trip;
import com.habeshago.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRequestRepository extends JpaRepository<ItemRequest, Long> {

    List<ItemRequest> findByTrip(Trip trip);

    List<ItemRequest> findByTripId(Long tripId);

    List<ItemRequest> findBySenderUser(User senderUser);

    List<ItemRequest> findBySenderUserIdOrderByCreatedAtDesc(Long senderUserId);

    List<ItemRequest> findByTripIdAndStatus(Long tripId, RequestStatus status);

    // Count delivered requests for a traveler (for reputation)
    @Query("SELECT COUNT(r) FROM ItemRequest r WHERE r.trip.user.id = :travelerId AND r.status = 'DELIVERED'")
    long countDeliveredByTravelerId(@Param("travelerId") Long travelerId);
}
