package com.example.developeractivity.developer;

import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.Optional;

class GitHubRateLimitException extends RuntimeException {

	private final String retryAfter;
	private final String remaining;

	GitHubRateLimitException(HttpHeaders headers) {
		super("GitHub API rate limit exceeded");
		this.retryAfter = retryAfter(headers);
		this.remaining = headers.getFirst("X-RateLimit-Remaining");
	}

	Optional<Instant> retryAt() {
		if (retryAfter == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(Instant.ofEpochSecond(Long.parseLong(retryAfter)));
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	String remaining() {
		return remaining;
	}

	String reset() {
		return retryAfter;
	}

	private static String retryAfter(HttpHeaders headers) {
		return headers.getFirst("X-RateLimit-Reset");
	}
}
