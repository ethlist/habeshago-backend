package com.habeshago.trip;

import com.habeshago.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserOrderByDepartureDateDesc(User user);

    List<Trip> findByFromCityIgnoreCaseAndToCityIgnoreCaseAndDepartureDateAndStatus(
            String fromCity,
            String toCity,
            LocalDate departureDate,
            TripStatus status
    );

    @Query(value = "SELECT * FROM trips t WHERE t.status = CAST(:status AS VARCHAR) " +
           "AND (CAST(:fromCity AS VARCHAR) IS NULL OR CAST(:fromCity AS VARCHAR) = '' OR LOWER(t.from_city) LIKE LOWER(CONCAT('%', CAST(:fromCity AS VARCHAR), '%'))) " +
           "AND (CAST(:toCity AS VARCHAR) IS NULL OR CAST(:toCity AS VARCHAR) = '' OR LOWER(t.to_city) LIKE LOWER(CONCAT('%', CAST(:toCity AS VARCHAR), '%'))) " +
           "AND (CAST(:departureDate AS DATE) IS NULL OR t.departure_date = CAST(:departureDate AS DATE)) " +
           "ORDER BY t.departure_date ASC",
           nativeQuery = true)
    List<Trip> searchTrips(
            @Param("fromCity") String fromCity,
            @Param("toCity") String toCity,
            @Param("departureDate") LocalDate departureDate,
            @Param("status") String status
    );

    /**
     * Update contact_value for all trips by a user that use TELEGRAM as contact method.
     * Called when user's Telegram username changes to keep contact info in sync.
     */
    @Modifying
    @Query("UPDATE Trip t SET t.contactValue = :newUsername, t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.user.id = :userId AND t.contactMethod = 'TELEGRAM'")
    int updateTelegramContactValue(@Param("userId") Long userId, @Param("newUsername") String newUsername);

    /**
     * Anonymize trips for a deleted user by clearing contact information.
     */
    @Modifying
    @Query("UPDATE Trip t SET t.contactMethod = NULL, t.contactValue = NULL, " +
           "t.contactTelegram = NULL, t.contactPhone = NULL, t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.user.id = :userId")
    int anonymizeUserTrips(@Param("userId") Long userId);
}
