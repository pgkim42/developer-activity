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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceTests {

	@Mock
	private GitHubClient gitHubClient;

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
	void translatesGitHubConnectionFailure() {
		when(gitHubClient.getUser("octocat"))
				.thenThrow(new ResourceAccessException("Connection timed out"));

		assertThatThrownBy(() -> developerService.getProfile("octocat"))
				.isInstanceOf(GitHubUnavailableException.class)
				.hasMessage("GitHub API is temporarily unavailable");
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
