package com.habeshago.notification;

import com.habeshago.telegram.TelegramClient;
import com.habeshago.telegram.TelegramMessage;
import com.habeshago.telegram.TelegramMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Service
@EnableScheduling
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationOutboxRepository outboxRepository;
    private final TelegramClient telegramClient;
    private final TelegramMessageFormatter messageFormatter;

    public NotificationService(
            NotificationOutboxRepository outboxRepository,
            TelegramClient telegramClient,
            TelegramMessageFormatter messageFormatter) {
        this.outboxRepository = outboxRepository;
        this.telegramClient = telegramClient;
        this.messageFormatter = messageFormatter;
    }

    @Scheduled(fixedDelayString = "10000")
    @Transactional
    public void processOutbox() {
        List<NotificationOutbox> pending = outboxRepository
                .findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                        EnumSet.of(OutboxStatus.PENDING, OutboxStatus.SENDING),
                        Instant.now()
                );

        for (NotificationOutbox entry : pending) {
            try {
                entry.setStatus(OutboxStatus.SENDING);
                outboxRepository.save(entry);

                // Format the message using the formatter
                TelegramMessage message = messageFormatter.formatNotification(entry);

                // Send with formatting and inline keyboard if present
                telegramClient.sendMessage(message, entry.getUser().getTelegramUserId());

                entry.setStatus(OutboxStatus.SENT);
                outboxRepository.save(entry);

                log.info("Sent notification {} to user {}", entry.getId(), entry.getUser().getId());
            } catch (Exception ex) {
                log.error("Error sending notification id {}: {}", entry.getId(), ex.getMessage());
                int retry = entry.getRetryCount() + 1;
                entry.setRetryCount(retry);
                if (retry > 5) {
                    entry.setStatus(OutboxStatus.FAILED);
                    log.warn("Notification {} failed after {} retries", entry.getId(), retry);
                } else {
                    entry.setStatus(OutboxStatus.PENDING);
                    entry.setNextAttemptAt(Instant.now().plusSeconds(60L * retry));
                }
                outboxRepository.save(entry);
            }
        }
    }

    @Transactional
    public void enqueueNotification(NotificationOutbox entry) {
        outboxRepository.save(entry);
        log.debug("Enqueued notification type={} for user={}", entry.getType(), entry.getUser().getId());
    }
}
