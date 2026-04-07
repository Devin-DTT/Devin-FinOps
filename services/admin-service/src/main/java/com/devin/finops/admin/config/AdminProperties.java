package com.devin.finops.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    private String redisKeyPrefix = "finops:endpoint:";
    private String enterpriseToken = "";
    private String orgToken = "";
}
