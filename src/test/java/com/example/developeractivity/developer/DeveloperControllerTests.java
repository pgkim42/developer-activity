package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(DeveloperController.class)
@AutoConfigureRestTestClient
class DeveloperControllerTests {

	@Autowired
	private RestTestClient restTestClient;

	@MockitoBean
	private DeveloperService developerService;

	@Test
	void returnsDeveloperProfile() {
		when(developerService.getProfile("octocat")).thenReturn(new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));

		restTestClient.get().uri("/developers/{username}", "octocat")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.username").isEqualTo("octocat")
				.jsonPath("$.publicRepositoryCount").isEqualTo(8)
				.jsonPath("$.followerCount").isEqualTo(17_905);
	}

	@Test
	void returnsNotModifiedWhenProfileEtagMatches() {
		DeveloperProfile profile = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(developerService.getProfile("octocat")).thenReturn(profile);

		EntityExchangeResult<byte[]> first = restTestClient.get().uri("/developers/{username}", "octocat")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult();
		String etag = first.getResponseHeaders().getETag();

		restTestClient.get().uri("/developers/{username}", "octocat")
				.header("If-None-Match", etag)
				.exchange()
				.expectStatus().isNotModified()
				.expectHeader().valueEquals("ETag", etag);
	}

	@Test
	void returnsRepositoriesWithDefaultPagination() {
		when(developerService.getRepositories("octocat", 1, 20)).thenReturn(List.of(
				repository()
		));

		restTestClient.get().uri("/developers/{username}/repositories", "octocat")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].name").isEqualTo("Spoon-Knife")
				.jsonPath("$[0].starCount").isEqualTo(13)
				.jsonPath("$[0].forkCount").isEqualTo(15)
				.jsonPath("$[0].repositoryUrl").isEqualTo("https://github.com/octocat/Spoon-Knife")
				.jsonPath("$[0].updatedAt").isEqualTo("2026-08-01T12:30:00Z");

		verify(developerService).getRepositories("octocat", 1, 20);
	}

	@Test
	void returnsActivitiesWithDefaultPagination() {
		when(developerService.getActivities("octocat", 1, 20)).thenReturn(List.of(
				new DeveloperActivity(
						"PushEvent", "octocat/Hello-World",
						"Pushed commits to octocat/Hello-World",
						Instant.parse("2026-08-16T12:30:00Z")
				)
		));

		restTestClient.get().uri("/developers/{username}/activities", "octocat")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].type").isEqualTo("PushEvent")
				.jsonPath("$[0].repository").isEqualTo("octocat/Hello-World");
	}

	@Test
	void returnsActivitySummary() {
		when(developerService.getActivitySummary("octocat")).thenReturn(
				new DeveloperActivitySummary(
						2,
						Map.of("PushEvent", 1, "IssuesEvent", 1),
						List.of(new RepositoryActivityCount("octocat/Hello-World", 2))
				)
		);

		restTestClient.get().uri("/developers/{username}/activity-summary", "octocat")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.eventCount").isEqualTo(2)
				.jsonPath("$.eventTypeCounts.PushEvent").isEqualTo(1)
				.jsonPath("$.repositories[0].activityCount").isEqualTo(2);
	}

	@Test
	void acceptsPaginationBoundaryValues() {
		when(developerService.getRepositories("octocat", 1, 100)).thenReturn(List.of());

		restTestClient.get().uri("/developers/{username}/repositories?page={page}&size={size}", "octocat", 1, 100)
				.exchange()
				.expectStatus().isOk();

		verify(developerService).getRepositories("octocat", 1, 100);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/developers/octocat/repositories?page=0&size=20",
			"/developers/octocat/repositories?page=1&size=0",
			"/developers/octocat/repositories?page=1&size=101"
	})
	void rejectsInvalidPagination(String path) {
		restTestClient.get().uri(path)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Invalid request");

		verifyNoInteractions(developerService);
	}

	@Test
	void returnsNotFoundProblem() {
		when(developerService.getProfile("missing-user"))
				.thenThrow(new DeveloperNotFoundException("missing-user"));

		restTestClient.get().uri("/developers/{username}", "missing-user")
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Developer not found")
				.jsonPath("$.detail").isEqualTo("Developer 'missing-user' was not found");
	}

	@Test
	void returnsBadGatewayProblem() {
		when(developerService.getProfile("octocat"))
				.thenThrow(new GitHubUnavailableException(new RuntimeException()));

		restTestClient.get().uri("/developers/{username}", "octocat")
				.exchange()
				.expectStatus().isEqualTo(502)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Upstream service unavailable");
	}

	@Test
	void returnsTooManyRequestsProblemForGitHubRateLimit() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-RateLimit-Reset", "1893456000");
		when(developerService.getProfile("octocat"))
				.thenThrow(new GitHubRateLimitException(headers));

		restTestClient.get().uri("/developers/{username}", "octocat")
				.exchange()
				.expectStatus().isEqualTo(429)
				.expectBody()
				.jsonPath("$.title").isEqualTo("GitHub API rate limit exceeded")
				.jsonPath("$.retryAfterSeconds").isNumber();
	}

	@Test
	void returnsNotFoundProblemForRepositories() {
		when(developerService.getRepositories("missing-user", 1, 20))
				.thenThrow(new DeveloperNotFoundException("missing-user"));

		restTestClient.get().uri("/developers/{username}/repositories", "missing-user")
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Developer not found");
	}

	@Test
	void returnsBadGatewayProblemForRepositories() {
		when(developerService.getRepositories("octocat", 1, 20))
				.thenThrow(new GitHubUnavailableException(new RuntimeException()));

		restTestClient.get().uri("/developers/{username}/repositories", "octocat")
				.exchange()
				.expectStatus().isEqualTo(502)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Upstream service unavailable");
	}

	@Test
	void rejectsInvalidGitHubUsernameBeforeCallingService() {
		restTestClient.get().uri("/developers/{username}", "-invalid-")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Invalid request");

		verifyNoInteractions(developerService);
	}

	@Test
	void rejectsInvalidGitHubUsernameForRepositories() {
		restTestClient.get().uri("/developers/{username}/repositories", "-invalid-")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Invalid request");

		verifyNoInteractions(developerService);
	}

	private DeveloperRepository repository() {
		return new DeveloperRepository(
				"Spoon-Knife",
				"Demonstration repository",
				"HTML",
				13,
				15,
				"https://github.com/octocat/Spoon-Knife",
				Instant.parse("2026-08-01T12:30:00Z")
		);
	}
}
