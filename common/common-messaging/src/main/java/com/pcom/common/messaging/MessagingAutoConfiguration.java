package com.pcom.common.messaging;

import com.pcom.common.dto.event.Topics;
import com.pcom.common.persistence.OutboxRepository;
import com.pcom.common.persistence.PersistenceAutoConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration(after = {PersistenceAutoConfiguration.class, KafkaAutoConfiguration.class})
@ConditionalOnClass(KafkaTemplate.class)
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    /** Declares the platform topics (retry / DLT topics are created by @RetryableTopic). */
    @Bean
    KafkaAdmin.NewTopics platformTopics(MessagingProperties p) {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .map(name -> TopicBuilder.name(name)
                        .partitions(p.getPartitions())
                        .replicas(p.getReplicas())
                        .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(p.getMinInsyncReplicas()))
                        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(p.getRetentionMs()))
                        .build())
                .toArray(NewTopic[]::new));
    }

    @Bean
    OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, MessagingProperties props) {
        return new OutboxRelay(repository, kafka, props);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    OutboxAdminController outboxAdminController(OutboxRepository repository) {
        return new OutboxAdminController(repository);
    }
}
