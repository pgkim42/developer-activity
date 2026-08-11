package com.example.developeractivity.developer;

public record DeveloperProfile(
		String username,
		String name,
		String profileUrl,
		String avatarUrl,
		int publicRepositoryCount,
		int followerCount
) {
	static DeveloperProfile from(GitHubUserResponse user) {
		return new DeveloperProfile(
				user.login(),
				user.name(),
				user.htmlUrl(),
				user.avatarUrl(),
				user.publicRepos(),
				user.followers()
		);
	}
}
