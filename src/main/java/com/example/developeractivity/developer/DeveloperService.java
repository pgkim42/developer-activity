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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import java.util.Comparator;
import java.util.stream.Collectors;

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
			countHit();
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
				countStale();
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
			countHit();
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
				countStale();
				return stale;
			}
			throw exception;
		}
	}

	List<DeveloperActivity> getActivities(String username, int page, int size) {
		String key = "activities:" + username + ":" + page + ":" + size;
		@SuppressWarnings("unchecked")
		List<DeveloperActivity> fresh = cache == null
				? null
				: (List<DeveloperActivity>) cache.fresh(key);
		if (fresh != null) {
			countHit();
			return fresh;
		}
		try {
			List<DeveloperActivity> activities = callGitHub(
					username,
					() -> gitHubClient.getEvents(username, page, size)
							.stream()
							.map(DeveloperActivity::from)
							.toList()
			);
			if (cache != null) {
				cache.put(key, activities);
			}
			return activities;
		} catch (RuntimeException exception) {
			@SuppressWarnings("unchecked")
			List<DeveloperActivity> stale = cache == null
					? null
					: (List<DeveloperActivity>) cache.stale(key);
			if (stale != null && isUpstreamFailure(exception)) {
				countStale();
				return stale;
			}
			throw exception;
		}
	}

	DeveloperActivitySummary getActivitySummary(String username) {
		Instant since = Instant.now().minus(Duration.ofDays(30));
		List<DeveloperActivity> activities = getActivities(username, 1, 100).stream()
				.filter(activity -> activity.occurredAt() != null)
				.filter(activity -> !activity.occurredAt().isBefore(since))
				.toList();

		Map<String, Integer> typeCounts = new LinkedHashMap<>();
		activities.forEach(activity ->
				typeCounts.merge(activity.type(), 1, Integer::sum));

		List<RepositoryActivityCount> repositories = activities.stream()
				.filter(activity -> activity.repository() != null)
				.collect(Collectors.groupingBy(
						DeveloperActivity::repository,
						Collectors.summingInt(activity -> 1)
				))
				.entrySet()
				.stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
						.thenComparing(Map.Entry::getKey))
				.map(entry -> new RepositoryActivityCount(entry.getKey(), entry.getValue()))
				.toList();

		return new DeveloperActivitySummary(activities.size(), typeCounts, repositories);
	}

	private <T> T callGitHub(String username, Supplier<T> request) {
		Instant started = Instant.now();
		String outcome = "success";
		try {
			return request.get();
		} catch (HttpClientErrorException.NotFound exception) {
			outcome = "not_found";
			throw new DeveloperNotFoundException(username);
		} catch (HttpClientErrorException exception) {
			if (isRateLimited(exception)) {
				outcome = "rate_limited";
				throw new GitHubRateLimitException(exception.getResponseHeaders());
			}
			outcome = "unavailable";
			throw new GitHubUnavailableException(exception);
		} catch (ResourceAccessException exception) {
			if (hasTimeoutCause(exception)) {
				outcome = "timeout";
				throw new GitHubTimeoutException(exception);
			}
			outcome = "unavailable";
			throw new GitHubUnavailableException(exception);
		} catch (RestClientException exception) {
			outcome = "unavailable";
			throw new GitHubUnavailableException(exception);
		} finally {
			recordDuration(Duration.between(started, Instant.now()), outcome);
		}
	}

	private boolean isUpstreamFailure(RuntimeException exception) {
		return exception instanceof GitHubTimeoutException
				|| exception instanceof GitHubUnavailableException
				|| exception instanceof GitHubRateLimitException;
	}

	private void countHit() {
		if (meterRegistry != null) {
			meterRegistry.counter("developer.cache.hits").increment();
		}
	}

	private void countStale() {
		if (meterRegistry != null) {
			meterRegistry.counter("developer.cache.stale").increment();
		}
	}

	private void recordDuration(Duration duration, String outcome) {
		if (meterRegistry != null) {
			meterRegistry.timer("developer.github.calls", "outcome", outcome).record(duration);
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
