package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeveloperController.class)
class DeveloperControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DeveloperService developerService;

	@Test
	void returnsDeveloperProfile() throws Exception {
		when(developerService.getProfile("octocat")).thenReturn(new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));

		mockMvc.perform(get("/developers/{username}", "octocat"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("octocat"))
				.andExpect(jsonPath("$.publicRepositoryCount").value(8))
				.andExpect(jsonPath("$.followerCount").value(17_905));
	}

	@Test
	void returnsNotModifiedWhenProfileEtagMatches() throws Exception {
		DeveloperProfile profile = new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		);
		when(developerService.getProfile("octocat")).thenReturn(profile);

		MvcResult first = mockMvc.perform(get("/developers/{username}", "octocat"))
				.andExpect(status().isOk())
				.andReturn();
		String etag = first.getResponse().getHeader("ETag");

		mockMvc.perform(get("/developers/{username}", "octocat")
						.header("If-None-Match", etag))
				.andExpect(status().isNotModified())
				.andExpect(header().string("ETag", etag));
	}

	@Test
	void returnsRepositoriesWithDefaultPagination() throws Exception {
		when(developerService.getRepositories("octocat", 1, 20)).thenReturn(List.of(
				repository()
		));

		mockMvc.perform(get("/developers/{username}/repositories", "octocat"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Spoon-Knife"))
				.andExpect(jsonPath("$[0].starCount").value(13))
				.andExpect(jsonPath("$[0].forkCount").value(15))
				.andExpect(jsonPath("$[0].repositoryUrl")
						.value("https://github.com/octocat/Spoon-Knife"))
				.andExpect(jsonPath("$[0].updatedAt").value("2026-08-01T12:30:00Z"));

		verify(developerService).getRepositories("octocat", 1, 20);
	}

	@Test
	void acceptsPaginationBoundaryValues() throws Exception {
		when(developerService.getRepositories("octocat", 1, 100)).thenReturn(List.of());

		mockMvc.perform(get("/developers/{username}/repositories", "octocat")
						.queryParam("page", "1")
						.queryParam("size", "100"))
				.andExpect(status().isOk());

		verify(developerService).getRepositories("octocat", 1, 100);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/developers/octocat/repositories?page=0&size=20",
			"/developers/octocat/repositories?page=1&size=0",
			"/developers/octocat/repositories?page=1&size=101"
	})
	void rejectsInvalidPagination(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));

		verifyNoInteractions(developerService);
	}

	@Test
	void returnsNotFoundProblem() throws Exception {
		when(developerService.getProfile("missing-user"))
				.thenThrow(new DeveloperNotFoundException("missing-user"));

		mockMvc.perform(get("/developers/{username}", "missing-user"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Developer not found"))
				.andExpect(jsonPath("$.detail").value("Developer 'missing-user' was not found"));
	}

	@Test
	void returnsBadGatewayProblem() throws Exception {
		when(developerService.getProfile("octocat"))
				.thenThrow(new GitHubUnavailableException(new RuntimeException()));

		mockMvc.perform(get("/developers/{username}", "octocat"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.title").value("Upstream service unavailable"));
	}

	@Test
	void returnsTooManyRequestsProblemForGitHubRateLimit() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-RateLimit-Reset", "1893456000");
		when(developerService.getProfile("octocat"))
				.thenThrow(new GitHubRateLimitException(headers));

		mockMvc.perform(get("/developers/{username}", "octocat"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.title").value("GitHub API rate limit exceeded"))
				.andExpect(jsonPath("$.retryAfterSeconds").isNumber());
	}

	@Test
	void returnsNotFoundProblemForRepositories() throws Exception {
		when(developerService.getRepositories("missing-user", 1, 20))
				.thenThrow(new DeveloperNotFoundException("missing-user"));

		mockMvc.perform(get("/developers/{username}/repositories", "missing-user"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Developer not found"));
	}

	@Test
	void returnsBadGatewayProblemForRepositories() throws Exception {
		when(developerService.getRepositories("octocat", 1, 20))
				.thenThrow(new GitHubUnavailableException(new RuntimeException()));

		mockMvc.perform(get("/developers/{username}/repositories", "octocat"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.title").value("Upstream service unavailable"));
	}

	@Test
	void rejectsInvalidGitHubUsernameBeforeCallingService() throws Exception {
		mockMvc.perform(get("/developers/{username}", "-invalid-"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));

		verifyNoInteractions(developerService);
	}

	@Test
	void rejectsInvalidGitHubUsernameForRepositories() throws Exception {
		mockMvc.perform(get("/developers/{username}/repositories", "-invalid-"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));

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
