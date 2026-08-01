package com.smartstudy.studyroom.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI studyRoomOpenApi(
            @Value("${springdoc.server-url:http://localhost:8080}")
            String serverUrl) {

        return new OpenAPI()
                .info(new Info()
                        .title("Smart Study Room Reservation and Fulfillment Governance System")
                        .version("1.0.0")
                        .description("Study room reservation, slot occupancy, fulfillment governance, Redis projection, and RabbitMQ timeout workflow APIs"))
                .servers(List.of(new Server()
                        .url(serverUrl)
                        .description("Current environment")));
    }
}