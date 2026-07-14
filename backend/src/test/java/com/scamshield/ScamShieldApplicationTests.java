package com.scamshield;

import static org.assertj.core.api.Assertions.assertThat;

import com.scamshield.support.TestSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestSecrets.JWT_PROP)
class ScamShieldApplicationTests {

    // pgvector image, presented to Testcontainers as a Postgres-compatible substitute.
    // @ServiceConnection wires spring.datasource.* to this container automatically.
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private TestRestTemplate rest;

    @Test
    void contextLoadsAndFlywayMigrationApplies() {
        // Reaching this assertion proves the pgvector container started and Flyway V1
        // applied cleanly — including CREATE EXTENSION vector and both HNSW indexes —
        // because the Spring context fails to start otherwise.
        assertThat(POSTGRES.isRunning()).isTrue();
    }

    @Test
    void healthEndpointReturns200Up() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
