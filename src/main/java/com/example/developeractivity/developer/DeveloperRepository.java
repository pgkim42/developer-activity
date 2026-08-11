package com.example.developeractivity.developer;

import java.time.Instant;

public record DeveloperRepository(
		String name,
		String description,
		String language,
		int starCount,
		int forkCount,
		String repositoryUrl,
		Instant updatedAt
) {
	static DeveloperRepository from(GitHubRepositoryResponse repository) {
		return new DeveloperRepository(
				repository.name(),
				repository.description(),
				repository.language(),
				repository.stargazersCount(),
				repository.forksCount(),
				repository.htmlUrl(),
				repository.updatedAt()
		);
	}
}
