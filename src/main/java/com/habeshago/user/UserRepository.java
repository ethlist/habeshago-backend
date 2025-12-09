package com.habeshago.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramUserId(Long telegramUserId);

    // Case-insensitive email lookup
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    // Case-insensitive email existence check
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    /**
     * Atomically update user rating to prevent race conditions.
     * Uses database-level arithmetic to ensure consistent results under concurrent updates.
     */
    @Modifying
    @Query("UPDATE User u SET " +
           "u.ratingCount = u.ratingCount + 1, " +
           "u.ratingAverage = CASE " +
           "  WHEN u.ratingCount = 0 OR u.ratingAverage IS NULL THEN CAST(:rating AS double) " +
           "  ELSE ((u.ratingAverage * u.ratingCount) + :rating) / (u.ratingCount + 1) " +
           "END, " +
           "u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.id = :userId")
    void updateRatingAtomically(@Param("userId") Long userId, @Param("rating") int rating);
}
