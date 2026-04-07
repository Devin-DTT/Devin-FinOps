package com.devin.finops.billing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    private String redisKeyPrefix = "finops:endpoint:";
    private String enterpriseToken = "";
}
