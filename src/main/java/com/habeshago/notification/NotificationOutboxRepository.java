package com.habeshago.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            java.util.Collection<OutboxStatus> statuses,
            Instant now
    );
}
