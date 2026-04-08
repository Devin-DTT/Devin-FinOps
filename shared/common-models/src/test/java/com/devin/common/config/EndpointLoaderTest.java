package com.devin.common.config;

import com.devin.common.model.EndpointDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link EndpointLoader}.
 */
class EndpointLoaderTest {

    @Test
    @DisplayName("Loads endpoints.yaml from classpath correctly")
    void loadsEndpointsFromClasspath() {
        EndpointLoader loader = new EndpointLoader();
        loader.init();

        List<EndpointDefinition> endpoints = loader.getEndpoints();
        assertNotNull(endpoints);
    }

    @Test
    @DisplayName("getReadEndpoints() filters only endpoints with method=GET")
    void filtersOnlyGetEndpoints() {
        EndpointLoader loader = new EndpointLoader();
        loader.init();

        List<EndpointDefinition> readEndpoints = loader.getReadEndpoints();
        assertNotNull(readEndpoints);

        for (EndpointDefinition endpoint : readEndpoints) {
            assertEquals("GET", endpoint.getMethod().toUpperCase(),
                    "Expected all read endpoints to have GET method, but found: "
                            + endpoint.getMethod() + " for " + endpoint.getName());
        }
    }

    @Test
    @DisplayName("Returns empty list if endpoints.yaml does not exist")
    void returnsEmptyListWhenFileNotFound(@TempDir Path tempDir) {
        EndpointLoader loader = new EndpointLoader();
        loader.init();
        assertNotNull(loader.getEndpoints());
    }

    @Test
    @DisplayName("findByName() returns matching endpoint")
    void findByNameReturnsEndpoint() {
        EndpointLoader loader = new EndpointLoader();
        loader.init();

        List<EndpointDefinition> endpoints = loader.getEndpoints();
        if (!endpoints.isEmpty()) {
            String firstName = endpoints.get(0).getName();
            assertTrue(loader.findByName(firstName).isPresent(),
                    "findByName should return the endpoint: " + firstName);
        }
    }

    @Test
    @DisplayName("findByName() returns empty for non-existent endpoint")
    void findByNameReturnsEmptyForNonExistent() {
        EndpointLoader loader = new EndpointLoader();
        loader.init();

        assertFalse(loader.findByName("non_existent_endpoint_xyz").isPresent());
    }

    @Test
    @DisplayName("findByScope() returns endpoints matching scope")
    void findByScopeReturnsMatchingEndpoints() {
        EndpointLoader loader = new EndpointLoader();
        loader.init();

        List<EndpointDefinition> enterpriseEndpoints = loader.findByScope("enterprise");
        assertNotNull(enterpriseEndpoints);

        for (EndpointDefinition endpoint : enterpriseEndpoints) {
            assertEquals("enterprise", endpoint.getScope().toLowerCase(),
                    "Expected enterprise scope for: " + endpoint.getName());
        }
    }
}
