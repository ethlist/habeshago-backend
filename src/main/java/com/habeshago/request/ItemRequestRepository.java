package com.habeshago.request;

import com.habeshago.trip.Trip;
import com.habeshago.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Update sender_contact_value for all requests by a user that use TELEGRAM as contact method.
     * Called when user's Telegram username changes to keep contact info in sync.
     */
    @Modifying
    @Query("UPDATE ItemRequest r SET r.senderContactValue = :newUsername, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.senderUser.id = :userId AND r.senderContactMethod = 'TELEGRAM'")
    int updateSenderTelegramContactValue(@Param("userId") Long userId, @Param("newUsername") String newUsername);

    /**
     * Anonymize requests for a deleted user by clearing contact information.
     */
    @Modifying
    @Query("UPDATE ItemRequest r SET r.senderContactMethod = NULL, r.senderContactValue = NULL, " +
           "r.senderContactTelegram = NULL, r.senderContactPhone = NULL, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.senderUser.id = :userId")
    int anonymizeUserRequests(@Param("userId") Long userId);
}
