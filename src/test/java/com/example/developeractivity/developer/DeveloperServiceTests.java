package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceTests {

	@Mock
	private GitHubClient gitHubClient;

	@Mock
	private DeveloperCache cache;

	@InjectMocks
	private DeveloperService developerService;

	@Test
	void mapsGitHubUserToDeveloperProfile() {
		GitHubUserResponse user = new GitHubUserResponse(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(gitHubClient.getUser("octocat")).thenReturn(user);

		DeveloperProfile profile = developerService.getProfile("octocat");

		assertThat(profile).isEqualTo(new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));
	}

	@Test
	void returnsFreshlyCachedProfileWithoutCallingGitHub() {
		DeveloperProfile cached = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(cache.fresh("profile:octocat")).thenReturn(cached);

		assertThat(developerService.getProfile("octocat")).isEqualTo(cached);

		verifyNoInteractions(gitHubClient);
	}

	@Test
	void servesStaleProfileWhenGitHubIsUnavailable() {
		DeveloperProfile cached = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(cache.stale("profile:octocat")).thenReturn(cached);
		when(gitHubClient.getUser("octocat"))
				.thenThrow(new ResourceAccessException("Connection failed"));

		assertThat(developerService.getProfile("octocat")).isEqualTo(cached);
	}

	@Test
	void doesNotServeStaleProfileWhenDeveloperDoesNotExist() {
		DeveloperProfile cached = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(cache.stale("profile:missing-user")).thenReturn(cached);
		HttpClientErrorException notFound = HttpClientErrorException.create(
				HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("missing-user")).thenThrow(notFound);

		assertThatThrownBy(() -> developerService.getProfile("missing-user"))
				.isInstanceOf(DeveloperNotFoundException.class);
	}

	@Test
	void translatesGitHubTooManyRequests() {
		HttpClientErrorException limited = HttpClientErrorException.create(
				HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
				HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("octocat")).thenThrow(limited);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubRateLimitException.class);
	}

	@Test
	void mapsGitHubRepositoriesToPublicResponse() {
		GitHubRepositoryResponse repository = new GitHubRepositoryResponse(
				"Spoon-Knife",
				"Demonstration repository",
				"HTML",
				13,
				15,
				"https://github.com/octocat/Spoon-Knife",
				Instant.parse("2026-08-01T12:30:00Z")
		);
		when(gitHubClient.getRepositories("octocat", 2, 30, "updated", "desc"))
				.thenReturn(List.of(repository));

		List<DeveloperRepository> repositories = developerService.getRepositories("octocat", 2, 30);

		assertThat(repositories).containsExactly(new DeveloperRepository(
				"Spoon-Knife",
				"Demonstration repository",
				"HTML",
				13,
				15,
				"https://github.com/octocat/Spoon-Knife",
				Instant.parse("2026-08-01T12:30:00Z")
		));
	}

	@Test
	void mapsGitHubActivitiesToPublicResponse() {
		when(gitHubClient.getEvents("octocat", 1, 20)).thenReturn(List.of(
				new GitHubEventResponse(
						"1", "PushEvent", new GitHubEventRepository("octocat/Hello-World"),
						Instant.parse("2026-08-16T12:30:00Z")
				)
		));

		assertThat(developerService.getActivities("octocat", 1, 20))
				.containsExactly(new DeveloperActivity(
						"PushEvent",
						"octocat/Hello-World",
						"Pushed commits to octocat/Hello-World",
						Instant.parse("2026-08-16T12:30:00Z")
				));
	}

	@Test
	void summarizesRecentActivitiesByTypeAndRepository() {
		when(gitHubClient.getEvents("octocat", 1, 100)).thenReturn(List.of(
				new GitHubEventResponse(
						"1", "PushEvent", new GitHubEventRepository("octocat/Hello-World"),
						Instant.now().minus(Duration.ofDays(2))
				),
				new GitHubEventResponse(
						"2", "IssuesEvent", new GitHubEventRepository("octocat/Hello-World"),
						Instant.now().minus(Duration.ofDays(3))
				)
		));

		assertThat(developerService.getActivitySummary("octocat"))
				.isEqualTo(new DeveloperActivitySummary(
						2,
						Map.of("PushEvent", 1, "IssuesEvent", 1),
						List.of(new RepositoryActivityCount("octocat/Hello-World", 2))
				));
	}

	@Test
	void translatesGitHubNotFound() {
		HttpClientErrorException notFound = HttpClientErrorException.create(
				HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getUser("missing-user")).thenThrow(notFound);

		assertThatThrownBy(() -> developerService.getProfile("missing-user"))
				.isInstanceOf(DeveloperNotFoundException.class)
				.hasMessage("Developer 'missing-user' was not found");
	}

	@Test
	void translatesRepositoryNotFound() {
		HttpClientErrorException notFound = HttpClientErrorException.create(
				HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
		);
		when(gitHubClient.getRepositories("missing-user", 1, 20, "updated", "desc"))
				.thenThrow(notFound);

		assertThatThrownBy(() -> developerService.getRepositories("missing-user", 1, 20))
				.isInstanceOf(DeveloperNotFoundException.class)
				.hasMessage("Developer 'missing-user' was not found");
	}

	@Test
	void translatesGitHubRateLimit() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-RateLimit-Remaining", "0");
		headers.set("X-RateLimit-Reset", "1893456000");
		HttpClientErrorException limited = HttpClientErrorException.create(
				HttpStatus.FORBIDDEN, "Forbidden", headers, new byte[0], null
		);
		when(gitHubClient.getUser("octocat")).thenThrow(limited);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubRateLimitException.class);
	}

	@Test
	void doesNotClassifyTimeoutFromMessageAlone() {
		when(gitHubClient.getUser("octocat"))
				.thenThrow(new ResourceAccessException("Connection timed out"));

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubUnavailableException.class)
				.hasMessage("GitHub API is temporarily unavailable");
	}

	@Test
	void translatesGitHubProfileTimeout() {
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpConnectTimeoutException("connect timed out")
		);
		when(gitHubClient.getUser("octocat")).thenThrow(timeout);

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubTimeoutException.class)
				.hasMessage("GitHub API did not respond in time");
	}

	@Test
	void translatesGitHubRepositoryTimeout() {
		ResourceAccessException timeout = new ResourceAccessException(
				"I/O error", new HttpTimeoutException("request timed out")
		);
		when(gitHubClient.getRepositories("octocat", 1, 20, "updated", "desc"))
				.thenThrow(timeout);

		assertThatThrownBy(() -> developerService.getRepositories("octocat", 1, 20))
				.isInstanceOf(GitHubTimeoutException.class)
				.hasMessage("GitHub API did not respond in time");
	}

	@Test
	void translatesRepositoryConnectionFailure() {
		when(gitHubClient.getRepositories("octocat", 1, 20, "updated", "desc"))
				.thenThrow(new ResourceAccessException("Connection failed"));

		assertThatThrownBy(() -> developerService.getRepositories("octocat", 1, 20))
				.isInstanceOf(GitHubUnavailableException.class)
				.hasMessage("GitHub API is temporarily unavailable");
	}
}
