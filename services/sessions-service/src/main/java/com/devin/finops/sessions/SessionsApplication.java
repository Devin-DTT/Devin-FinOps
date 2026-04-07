package com.devin.finops.sessions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.devin.finops.sessions", "com.devin.common"})
public class SessionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionsApplication.class, args);
    }
}
