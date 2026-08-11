package com.example.developeractivity.developer;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record GitHubRepositoryResponse(
		String name,
		String description,
		String language,
		int stargazersCount,
		int forksCount,
		String htmlUrl,
		Instant updatedAt
) {
}
