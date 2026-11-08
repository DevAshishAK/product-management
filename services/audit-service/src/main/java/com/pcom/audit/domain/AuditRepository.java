package com.pcom.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditRepository extends MongoRepository<AuditRecord, String> {
    Page<AuditRecord> findByAggregateId(String aggregateId, Pageable pageable);
    Page<AuditRecord> findByEventType(String eventType, Pageable pageable);
}
