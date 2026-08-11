package com.example.developeractivity.developer;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record GitHubUserResponse(
		String login,
		String name,
		String htmlUrl,
		String avatarUrl,
		int publicRepos,
		int followers
) {
}
