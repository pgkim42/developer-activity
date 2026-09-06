package com.example.developeractivity.developer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import java.net.http.HttpConnectTimeoutException;

import io.micrometer.core.instrument.Timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceMetricsTests {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private DeveloperCache cache;

	private MeterRegistry meterRegistry;
	private DeveloperService developerService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		developerService = new DeveloperService(gitHubClient, cache, meterRegistry, Tracer.NOOP);
	}

	@Test
	void recordsCacheHitWhenFreshProfileIsServed() {
		DeveloperProfile cached = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(cache.fresh("profile:octocat")).thenReturn(cached);

		assertThat(developerService.getProfile("octocat")).isEqualTo(cached);

		assertThat(meterRegistry.counter("developer.cache.hits").count()).isEqualTo(1.0);
		verifyNoInteractions(gitHubClient);
	}

	@Test
	void recordsSuccessfulGitHubCallDuration() {
		when(gitHubClient.getUser("octocat")).thenReturn(new GitHubUserResponse(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));

		developerService.getProfile("octocat");

		Timer timer = meterRegistry.find("developer.github.calls")
				.tag("outcome", "success")
				.timer();
		assertThat(timer).isNotNull();
		assertThat(timer.count()).isEqualTo(1L);
	}

	@Test
	void recordsStaleCacheWhenGitHubIsUnavailable() {
		DeveloperProfile cached = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(cache.stale("profile:octocat")).thenReturn(cached);
		when(gitHubClient.getUser("octocat"))
				.thenThrow(new ResourceAccessException("Connection failed"));

		assertThat(developerService.getProfile("octocat")).isEqualTo(cached);

		assertThat(meterRegistry.counter("developer.cache.stale").count()).isEqualTo(1.0);
		verify(gitHubClient, times(1)).getUser("octocat");
	}

	@Test
	void recordsTimeoutWhenGitHubDoesNotRespond() {
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpConnectTimeoutException("connect timed out")
		);
		when(gitHubClient.getUser("octocat")).thenThrow(timeout);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubTimeoutException.class);

		assertOutcomeCount("timeout", 2L);
		verify(gitHubClient, times(2)).getUser("octocat");
	}

	@Test
	void recordsUnavailableWhenGitHubConnectionFails() {
		when(gitHubClient.getUser("octocat"))
				.thenThrow(new ResourceAccessException("Connection failed"));

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubUnavailableException.class);

		assertOutcomeCount("unavailable", 1L);
		verify(gitHubClient, times(1)).getUser("octocat");
	}

	@Test
	void recordsUnavailableWhenGitHubReturnsServerError() {
		HttpServerErrorException serverError = HttpServerErrorException.create(
				HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("octocat")).thenThrow(serverError);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubUnavailableException.class);

		assertOutcomeCount("unavailable", 2L);
		verify(gitHubClient, times(2)).getUser("octocat");
	}

	@Test
	void recordsRateLimitedWhenGitHubRejectsTheCall() {
		HttpClientErrorException limited = HttpClientErrorException.create(
				HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
				HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("octocat")).thenThrow(limited);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubRateLimitException.class);

		assertOutcomeCount("rate_limited", 1L);
		verify(gitHubClient, times(1)).getUser("octocat");
	}

	@Test
	void doesNotRecordCacheHitWhenDeveloperIsMissing() {
		HttpClientErrorException notFound = HttpClientErrorException.create(
				HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("missing-user")).thenThrow(notFound);

		assertThatThrownBy(() -> developerService.getProfile("missing-user"))
				.isInstanceOf(DeveloperNotFoundException.class);

		assertThat(meterRegistry.find("developer.cache.hits").counter()).isNull();
		assertOutcomeCount("not_found", 1L);
		verify(gitHubClient, times(1)).getUser("missing-user");
	}

	@Test
	void retriesTimeoutOnceAndRecordsEachAttempt() {
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpConnectTimeoutException("connect timed out")
		);
		when(gitHubClient.getUser("octocat"))
				.thenThrow(timeout)
				.thenReturn(new GitHubUserResponse(
						"octocat", "The Octocat", "https://github.com/octocat",
						"https://avatars.githubusercontent.com/u/583231", 8, 17_905
				));

		developerService.getProfile("octocat");

		verify(gitHubClient, times(2)).getUser("octocat");
		assertOutcomeCount("timeout", 1L);
		assertOutcomeCount("success", 1L);
	}

	@Test
	void retriesServerErrorOnceAndRecordsEachAttempt() {
		HttpServerErrorException serverError = HttpServerErrorException.create(
				HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("octocat"))
				.thenThrow(serverError)
				.thenReturn(new GitHubUserResponse(
						"octocat", "The Octocat", "https://github.com/octocat",
						"https://avatars.githubusercontent.com/u/583231", 8, 17_905
				));

		developerService.getProfile("octocat");

		verify(gitHubClient, times(2)).getUser("octocat");
		assertOutcomeCount("unavailable", 1L);
		assertOutcomeCount("success", 1L);
	}

	@Test
	void doesNotRetryClientError() {
		HttpClientErrorException badRequest = HttpClientErrorException.create(
				HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("octocat")).thenThrow(badRequest);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubUnavailableException.class);

		verify(gitHubClient, times(1)).getUser("octocat");
		assertOutcomeCount("unavailable", 1L);
	}

	@Test
	void doesNotRetryTimeoutWhenStaleCacheExists() {
		DeveloperProfile cached = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpConnectTimeoutException("connect timed out")
		);
		when(cache.stale("profile:octocat")).thenReturn(cached);
		when(gitHubClient.getUser("octocat")).thenThrow(timeout);

		assertThat(developerService.getProfile("octocat")).isEqualTo(cached);

		verify(gitHubClient, times(1)).getUser("octocat");
		assertThat(meterRegistry.counter("developer.cache.stale").count()).isEqualTo(1.0);
		assertOutcomeCount("timeout", 1L);
	}

	private void assertOutcomeCount(String outcome, long expected) {
		Timer timer = meterRegistry.find("developer.github.calls")
				.tag("outcome", outcome)
				.timer();
		assertThat(timer).isNotNull();
		assertThat(timer.count()).isEqualTo(expected);
	}
}
