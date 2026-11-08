package com.pcom.common.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {
    private int partitions = 6;
    private int replicas = 1;
    private int minInsyncReplicas = 1;
    private long retentionMs = 7L * 24 * 3600 * 1000;
    private int outboxBatchSize = 100;
    private int outboxRetentionDays = 7;
}
