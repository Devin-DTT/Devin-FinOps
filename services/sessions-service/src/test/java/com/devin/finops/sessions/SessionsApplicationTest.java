package com.devin.finops.sessions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
class SessionsApplicationTest {

    @Test
    void contextLoads() {
        // Verifies Spring context loads without errors
    }
}
