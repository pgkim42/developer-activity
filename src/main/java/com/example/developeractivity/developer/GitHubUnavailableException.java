package com.example.developeractivity.developer;

final class GitHubUnavailableException extends RuntimeException {

	GitHubUnavailableException(Throwable cause) {
		super("GitHub API is temporarily unavailable", cause);
	}
}
