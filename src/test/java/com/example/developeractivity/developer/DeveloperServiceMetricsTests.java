package com.example.developeractivity.developer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.micrometer.core.instrument.Timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
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
		developerService = new DeveloperService(gitHubClient, cache, meterRegistry);
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
		Timer timer = meterRegistry.find("developer.github.calls")
				.tag("outcome", "not_found")
				.timer();
		assertThat(timer).isNotNull();
		assertThat(timer.count()).isEqualTo(1L);
	}
}
