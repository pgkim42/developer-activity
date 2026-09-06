package com.example.developeractivity.developer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpConnectTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceSpanTests {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private DeveloperCache cache;

	private SimpleTracer tracer;
	private DeveloperService developerService;

	@BeforeEach
	void setUp() {
		tracer = new SimpleTracer();
		developerService = new DeveloperService(gitHubClient, cache, new SimpleMeterRegistry(), tracer);
	}

	@Test
	void freshCacheHitHasLookupSpanAndNoGitHubCallSpan() {
		when(cache.fresh("profile:octocat")).thenReturn(profile());

		developerService.getProfile("octocat");

		assertThat(spansNamed("developer.lookup")).singleElement().satisfies(span ->
				assertThat(span.getTags()).containsEntry("cache", "hit"));
		assertThat(spansNamed("github.call")).isEmpty();
		verifyNoInteractions(gitHubClient);
	}

	@Test
	void successfulGitHubCallRecordsMissAndAttempt() {
		when(gitHubClient.getUser("octocat")).thenReturn(user());

		developerService.getProfile("octocat");

		assertThat(spansNamed("developer.lookup")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("cache", "miss")
						.containsEntry("github.outcome", "success"));
		assertThat(spansNamed("github.call")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("attempt", "1")
						.containsEntry("github.outcome", "success"));
		assertChildOf("github.call", "developer.lookup");
	}

	@Test
	void staleCacheRecordsStaleLookupAndOneGitHubCall() {
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpConnectTimeoutException("connect timed out")
		);
		when(cache.stale("profile:octocat")).thenReturn(profile());
		when(gitHubClient.getUser("octocat")).thenThrow(timeout);

		assertThat(developerService.getProfile("octocat")).isEqualTo(profile());

		assertThat(spansNamed("developer.lookup")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("cache", "stale")
						.containsEntry("github.outcome", "timeout"));
		assertThat(spansNamed("github.call")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("attempt", "1")
						.containsEntry("github.outcome", "timeout"));
		assertChildOf("github.call", "developer.lookup");
		verify(gitHubClient, times(1)).getUser("octocat");
	}

	@Test
	void timeoutThenSuccessRecordsTwoGitHubAttempts() {
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpConnectTimeoutException("connect timed out")
		);
		when(gitHubClient.getUser("octocat"))
				.thenThrow(timeout)
				.thenReturn(user());

		developerService.getProfile("octocat");

		List<SimpleSpan> calls = spansNamed("github.call");
		assertThat(calls).hasSize(2);
		assertThat(calls.get(0).getTags())
				.containsEntry("attempt", "1")
				.containsEntry("github.outcome", "timeout");
		assertThat(calls.get(1).getTags())
				.containsEntry("attempt", "2")
				.containsEntry("github.outcome", "success");
		assertThat(spansNamed("developer.lookup")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("cache", "miss")
						.containsEntry("github.outcome", "success"));
		assertChildOf("github.call", "developer.lookup");
	}

	@Test
	void rateLimitDoesNotCreateRetrySpan() {
		HttpClientErrorException limited = HttpClientErrorException.create(
				HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
				HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("octocat")).thenThrow(limited);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubRateLimitException.class);

		assertThat(spansNamed("github.call")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("attempt", "1")
						.containsEntry("github.outcome", "rate_limited"));
		assertChildOf("github.call", "developer.lookup");
		verify(gitHubClient, times(1)).getUser("octocat");
	}

	@Test
	void notFoundDoesNotCreateRetrySpan() {
		HttpClientErrorException notFound = HttpClientErrorException.create(
				HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("missing-user")).thenThrow(notFound);

		assertThatThrownBy(() -> developerService.getProfile("missing-user"))
				.isInstanceOf(DeveloperNotFoundException.class);

		assertThat(spansNamed("github.call")).singleElement().satisfies(span ->
				assertThat(span.getTags())
						.containsEntry("attempt", "1")
						.containsEntry("github.outcome", "not_found"));
		assertChildOf("github.call", "developer.lookup");
		verify(gitHubClient, times(1)).getUser("missing-user");
	}

	private List<SimpleSpan> spansNamed(String name) {
		return tracer.getSpans().stream()
				.filter(span -> name.equals(span.getName()))
				.toList();
	}

	private void assertChildOf(String childName, String parentName) {
		SimpleSpan parent = spansNamed(parentName).getFirst();
		assertThat(spansNamed(childName)).isNotEmpty().allSatisfy(child ->
				assertThat(child.getParentId()).isEqualTo(parent.context().spanId()));
	}

	private static DeveloperProfile profile() {
		return new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
	}

	private static GitHubUserResponse user() {
		return new GitHubUserResponse(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
	}
}
