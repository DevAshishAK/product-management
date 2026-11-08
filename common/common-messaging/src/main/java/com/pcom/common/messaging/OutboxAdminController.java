package com.pcom.common.messaging;

import com.pcom.common.persistence.OutboxRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event replay: re-queues retained outbox rows of a time window for publication. Consumers deduplicate by eventId,
 * so replay into an EXISTING group needs its processed_event rows removed; for a NEW consumer group use a fresh groupId
 * (see scripts/replay-events.sh for the offset-reset alternative).
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
public class OutboxAdminController {
    private final OutboxRepository repository;

    public OutboxAdminController(OutboxRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/replay")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> replay(@RequestParam Instant from, @RequestParam Instant to) {
        return Map.of("requeued", repository.markForReplay(from, to));
    }
}
