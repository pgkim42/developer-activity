package com.example.developeractivity.developer;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record GitHubEventResponse(
        String id,
        String type,
        GitHubEventRepository repo,
        Instant createdAt
) {
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record GitHubEventRepository(String name) {
}
