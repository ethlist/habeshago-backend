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

    @Query("SELECT t FROM Trip t WHERE t.status = :status " +
           "AND (:fromCity IS NULL OR LOWER(t.fromCity) LIKE LOWER(CONCAT('%', :fromCity, '%'))) " +
           "AND (:toCity IS NULL OR LOWER(t.toCity) LIKE LOWER(CONCAT('%', :toCity, '%'))) " +
           "AND (:departureDate IS NULL OR t.departureDate = :departureDate) " +
           "ORDER BY t.departureDate ASC")
    List<Trip> searchTrips(
            @Param("fromCity") String fromCity,
            @Param("toCity") String toCity,
            @Param("departureDate") LocalDate departureDate,
            @Param("status") TripStatus status
    );
}
