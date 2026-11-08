package com.pcom.common.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcom.common.dto.event.EventSerde;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Registers shared entities/repositories (outbox, inbox, idempotency) and auditing in every JPA service. */
@AutoConfiguration
@AutoConfigurationPackage(basePackages = "com.pcom.common.persistence")
@ConditionalOnClass(EntityManager.class)
@Import(JpaAuditingConfig.class)
public class PersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EventSerde eventSerde(ObjectMapper mapper) {
        return new EventSerde(mapper);
    }

    @Bean
    OutboxService outboxService(OutboxRepository repository, EventSerde serde) {
        return new OutboxService(repository, serde);
    }

    @Bean
    EventProcessor eventProcessor(ProcessedEventRepository repository, EventSerde serde) {
        return new EventProcessor(repository, serde);
    }

    @Bean
    IdempotencyService idempotencyService(IdempotencyRecordRepository repository, ObjectMapper mapper) {
        return new IdempotencyService(repository, mapper);
    }
}
