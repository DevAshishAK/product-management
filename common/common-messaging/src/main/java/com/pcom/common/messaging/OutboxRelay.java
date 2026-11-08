package com.pcom.common.messaging;

import com.pcom.common.persistence.OutboxEvent;
import com.pcom.common.persistence.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** Polls the outbox table and publishes to Kafka (idempotent producer, acks=all). Stops at first failure to keep order. */
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final MessagingProperties props;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, MessagingProperties props) {
        this.repository = repository;
        this.kafka = kafka;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.messaging.outbox-poll-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.lockBatch(props.getOutboxBatchSize());
        for (OutboxEvent event : batch) {
            ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getEventKey(), event.getPayload());
            record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));
            try {
                kafka.send(record).get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Outbox publish failed for {} - will retry: {}", event.getId(), e.getMessage());
                return;
            }
        }
    }

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purge() {
        int n = repository.purgePublishedBefore(Instant.now().minus(props.getOutboxRetentionDays(), ChronoUnit.DAYS));
        if (n > 0) log.info("Purged {} published outbox rows", n);
    }
}
