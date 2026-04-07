package com.devin.finops.sessions.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "sessions")
public class SessionsProperties {

    private String redisKeyPrefix = "finops:endpoint:";
    private String enterpriseToken = "";
    private String orgToken = "";
}
