package com.devin.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the DataCollectorApplication class.
 * Full context loading requires Redis; tested via integration tests.
 */
class DataCollectorApplicationTest {

    @Test
    void applicationClassExists() {
        assertThat(DataCollectorApplication.class).isNotNull();
    }
}
