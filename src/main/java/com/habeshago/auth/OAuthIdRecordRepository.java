package com.habeshago.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthIdRecordRepository extends JpaRepository<OAuthIdRecord, Long> {

    Optional<OAuthIdRecord> findByGoogleId(String googleId);

    Optional<OAuthIdRecord> findByTelegramUserId(Long telegramUserId);

    Optional<OAuthIdRecord> findByUserId(Long userId);

    boolean existsByGoogleId(String googleId);

    boolean existsByTelegramUserId(Long telegramUserId);
}
