package com.example.developeractivity.developer;

import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
class DeveloperService {

	private final GitHubClient gitHubClient;
	private final DeveloperCache cache;
	private final MeterRegistry meterRegistry;

	DeveloperProfile getProfile(String username) {
		String key = "profile:" + username;
		DeveloperProfile fresh = cache == null ? null : (DeveloperProfile) cache.fresh(key);
		if (fresh != null) {
			count("cache.hit");
			return fresh;
		}
		try {
			DeveloperProfile profile = callGitHub(username, () -> DeveloperProfile.from(gitHubClient.getUser(username)));
			if (cache != null) {
				cache.put(key, profile);
			}
			return profile;
		} catch (RuntimeException exception) {
			DeveloperProfile stale = cache == null ? null : (DeveloperProfile) cache.stale(key);
			if (stale != null && isUpstreamFailure(exception)) {
				count("cache.stale");
				return stale;
			}
			throw exception;
		}
	}

	List<DeveloperRepository> getRepositories(String username, int page, int size) {
		String key = "repositories:" + username + ":" + page + ":" + size;
		@SuppressWarnings("unchecked")
		List<DeveloperRepository> fresh = cache == null ? null : (List<DeveloperRepository>) cache.fresh(key);
		if (fresh != null) {
			count("cache.hit");
			return fresh;
		}
		try {
			List<DeveloperRepository> repositories = callGitHub(
					username,
					() -> gitHubClient.getRepositories(username, page, size, "updated", "desc")
							.stream()
							.map(DeveloperRepository::from)
							.toList()
			);
			if (cache != null) {
				cache.put(key, repositories);
			}
			return repositories;
		} catch (RuntimeException exception) {
			@SuppressWarnings("unchecked")
			List<DeveloperRepository> stale = cache == null
					? null
					: (List<DeveloperRepository>) cache.stale(key);
			if (stale != null && isUpstreamFailure(exception)) {
				count("cache.stale");
				return stale;
			}
			throw exception;
		}
	}

	private <T> T callGitHub(String username, Supplier<T> request) {
		Instant started = Instant.now();
		try {
			return request.get();
		} catch (HttpClientErrorException.NotFound exception) {
			throw new DeveloperNotFoundException(username);
		} catch (HttpClientErrorException exception) {
			if (isRateLimited(exception)) {
				throw new GitHubRateLimitException(exception.getResponseHeaders());
			}
			throw new GitHubUnavailableException(exception);
		} catch (ResourceAccessException exception) {
			if (hasTimeoutCause(exception)) {
				throw new GitHubTimeoutException(exception);
			}
			throw new GitHubUnavailableException(exception);
		} catch (RestClientException exception) {
			throw new GitHubUnavailableException(exception);
		} finally {
			recordDuration(Duration.between(started, Instant.now()));
		}
	}

	private boolean isUpstreamFailure(RuntimeException exception) {
		return exception instanceof GitHubTimeoutException
				|| exception instanceof GitHubUnavailableException
				|| exception instanceof GitHubRateLimitException;
	}

	private void count(String outcome) {
		if (meterRegistry != null) {
			meterRegistry.counter("developer.cache.requests", "outcome", outcome).increment();
		}
	}

	private void recordDuration(Duration duration) {
		if (meterRegistry != null) {
			meterRegistry.timer("github.client.requests").record(duration);
		}
	}

	private boolean isRateLimited(HttpClientErrorException exception) {
		return exception.getStatusCode().value() == 429
				|| ("0".equals(exception.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
				&& exception.getStatusCode().value() == 403);
	}

	private boolean hasTimeoutCause(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
				return true;
			}
		}
		return false;
	}
}
