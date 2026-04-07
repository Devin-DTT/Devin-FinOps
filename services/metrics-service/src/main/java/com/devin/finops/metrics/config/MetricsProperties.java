package com.devin.finops.metrics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "metrics-config")
public class MetricsProperties {

    private String redisKeyPrefix = "finops:endpoint:";
}
