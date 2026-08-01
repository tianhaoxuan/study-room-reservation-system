package com.smartstudy.studyroom;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DockerDeploymentConfigTest {

    @Test
    void shouldProvideDockerfileForSpringBootApplication()
            throws IOException {

        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile)
                .contains("maven:3.9.11-eclipse-temurin-17");
        assertThat(dockerfile)
                .contains("eclipse-temurin:17-jre");
        assertThat(dockerfile)
                .contains("study-room-reservation-1.0.0.jar");
        assertThat(dockerfile)
                .contains("EXPOSE 8080");
    }

    @Test
    void shouldProvideComposeServicesForApplicationDependencies()
            throws IOException {

        String compose = Files.readString(Path.of("compose.yaml"));

        assertThat(compose).contains("studyroom-mysql");
        assertThat(compose).contains("studyroom-redis");
        assertThat(compose).contains("studyroom-rabbitmq");
        assertThat(compose).contains("studyroom-app");

        assertThat(compose).contains("mysql:8.0.36");
        assertThat(compose).contains("redis:7.2-alpine");
        assertThat(compose).contains("rabbitmq:3.13-management");

        assertThat(compose).contains("DB_URL:");
        assertThat(compose).contains("REDIS_HOST: redis");
        assertThat(compose).contains("RABBITMQ_HOST: rabbitmq");
        assertThat(compose).contains("/actuator/health");
    }
}