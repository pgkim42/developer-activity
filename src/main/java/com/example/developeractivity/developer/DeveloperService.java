package com.example.developeractivity.developer;

import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DeveloperService {

	private final GitHubClient gitHubClient;
	private final DeveloperCache cache;
	private final MeterRegistry meterRegistry;
	private final Tracer tracer;
	private final RetryTemplate gitHubGetRetry = new RetryTemplate(
			RetryPolicy.builder()
					.maxRetries(1)
					.delay(Duration.ofMillis(50))
					.includes(GitHubTimeoutException.class, GitHubUnavailableException.class)
					.predicate(DeveloperService::isRetryableFailure)
					.build()
	);

	DeveloperProfile getProfile(String username) {
		return load(
				"profile:" + username,
				username,
				() -> DeveloperProfile.from(gitHubClient.getUser(username))
		);
	}

	List<DeveloperRepository> getRepositories(String username, int page, int size) {
		return load(
				"repositories:" + username + ":" + page + ":" + size,
				username,
				() -> gitHubClient.getRepositories(username, page, size, "updated", "desc")
						.stream()
						.map(DeveloperRepository::from)
						.toList()
		);
	}

	List<DeveloperActivity> getActivities(String username, int page, int size) {
		return load(
				"activities:" + username + ":" + page + ":" + size,
				username,
				() -> gitHubClient.getEvents(username, page, size)
						.stream()
						.map(DeveloperActivity::from)
						.toList()
		);
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

	@SuppressWarnings("unchecked")
	private <T> T load(String key, String username, Supplier<T> request) {
		Span lookup = startSpan("developer.lookup");
		try (Tracer.SpanInScope scope = inScope(lookup)) {
			T fresh = cache == null ? null : (T) cache.fresh(key);
			if (fresh != null) {
				countHit();
				lookup.tag("cache", "hit");
				return fresh;
			}
			boolean hasStale = cache != null && cache.stale(key) != null;
			try {
				T value = hasStale
						? callGitHub(username, request, 1)
						: invokeWithRetry(username, request);
				lookup.tag("cache", "miss");
				lookup.tag("github.outcome", "success");
				if (cache != null) {
					cache.put(key, value);
				}
				return value;
			} catch (RuntimeException exception) {
				T stale = cache == null ? null : (T) cache.stale(key);
				if (stale != null && isUpstreamFailure(exception)) {
					countStale();
					lookup.tag("cache", "stale");
					lookup.tag("github.outcome", outcomeOf(exception));
					return stale;
				}
				lookup.tag("cache", "miss");
				lookup.tag("github.outcome", outcomeOf(exception));
				throw exception;
			}
		} finally {
			lookup.end();
		}
	}

	private <T> T invokeWithRetry(String username, Supplier<T> request) {
		AtomicInteger attempt = new AtomicInteger();
		return gitHubGetRetry.invoke(() -> callGitHub(username, request, attempt.incrementAndGet()));
	}

	private <T> T callGitHub(String username, Supplier<T> request, int attempt) {
		Span span = startSpan("github.call");
		span.tag("attempt", String.valueOf(attempt));
		Instant started = Instant.now();
		String outcome = "success";
		try (Tracer.SpanInScope scope = inScope(span)) {
			try {
				T value = request.get();
				span.tag("github.outcome", outcome);
				return value;
			} catch (HttpClientErrorException.NotFound exception) {
				outcome = "not_found";
				span.tag("github.outcome", outcome);
				span.error(exception);
				throw new DeveloperNotFoundException(username);
			} catch (HttpClientErrorException exception) {
				if (isRateLimited(exception)) {
					outcome = "rate_limited";
					span.tag("github.outcome", outcome);
					span.error(exception);
					throw new GitHubRateLimitException(exception.getResponseHeaders());
				}
				outcome = "unavailable";
				span.tag("github.outcome", outcome);
				span.error(exception);
				throw new GitHubUnavailableException(exception);
			} catch (HttpServerErrorException exception) {
				outcome = "unavailable";
				span.tag("github.outcome", outcome);
				span.error(exception);
				throw new GitHubUnavailableException(exception);
			} catch (ResourceAccessException exception) {
				if (hasTimeoutCause(exception)) {
					outcome = "timeout";
					span.tag("github.outcome", outcome);
					span.error(exception);
					throw new GitHubTimeoutException(exception);
				}
				outcome = "unavailable";
				span.tag("github.outcome", outcome);
				span.error(exception);
				throw new GitHubUnavailableException(exception);
			} catch (RestClientException exception) {
				outcome = "unavailable";
				span.tag("github.outcome", outcome);
				span.error(exception);
				throw new GitHubUnavailableException(exception);
			} finally {
				recordDuration(Duration.between(started, Instant.now()), outcome);
			}
		} finally {
			span.end();
		}
	}

	private Span startSpan(String name) {
		return tracer.nextSpan().name(name).start();
	}

	private Tracer.SpanInScope inScope(Span span) {
		return tracer.withSpan(span);
	}

	private boolean isUpstreamFailure(RuntimeException exception) {
		return exception instanceof GitHubTimeoutException
				|| exception instanceof GitHubUnavailableException
				|| exception instanceof GitHubRateLimitException;
	}

	private static boolean isRetryableFailure(Throwable exception) {
		if (exception instanceof GitHubTimeoutException) {
			return true;
		}
		return exception instanceof GitHubUnavailableException
				&& exception.getCause() instanceof HttpServerErrorException;
	}

	private static String outcomeOf(RuntimeException exception) {
		if (exception instanceof GitHubTimeoutException) {
			return "timeout";
		}
		if (exception instanceof DeveloperNotFoundException) {
			return "not_found";
		}
		if (exception instanceof GitHubRateLimitException) {
			return "rate_limited";
		}
		return "unavailable";
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
