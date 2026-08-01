package com.smartstudy.studyroom;

import com.smartstudy.studyroom.config.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void shouldExposeOpenApiMetadata() {
        OpenApiConfig config = new OpenApiConfig();

        OpenAPI openAPI =
                config.studyRoomOpenApi("http://localhost:8080");

        assertThat(openAPI.getInfo().getTitle())
                .isEqualTo("Smart Study Room Reservation and Fulfillment Governance System");
        assertThat(openAPI.getInfo().getVersion())
                .isEqualTo("1.0.0");
        assertThat(openAPI.getServers())
                .hasSize(1);
        assertThat(openAPI.getServers().get(0).getUrl())
                .isEqualTo("http://localhost:8080");
    }
}