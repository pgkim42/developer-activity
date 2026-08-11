package com.example.developeractivity.developer;

final class GitHubTimeoutException extends RuntimeException {

	GitHubTimeoutException(Throwable cause) {
		super("GitHub API did not respond in time", cause);
	}
}
