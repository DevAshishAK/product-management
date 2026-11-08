package com.pcom.common.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several pods relay concurrently without double-publishing. */
    @Query(value = "select * from outbox_event where published = false order by created_at limit :limit for update skip locked",
            nativeQuery = true)
    List<OutboxEvent> lockBatch(@Param("limit") int limit);

    @Modifying
    @Query("delete from OutboxEvent o where o.published = true and o.publishedAt < :before")
    int purgePublishedBefore(@Param("before") Instant before);

    @Modifying
    @Query("update OutboxEvent o set o.published = false, o.publishedAt = null where o.createdAt between :from and :to")
    int markForReplay(@Param("from") Instant from, @Param("to") Instant to);
}
