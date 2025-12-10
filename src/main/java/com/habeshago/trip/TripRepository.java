package com.habeshago.trip;

import com.habeshago.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
