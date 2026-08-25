package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.http.serviceclient.github.base-url=https://api.github.test",
		"github.api.token="
})
@AutoConfigureMockRestServiceServer
class GitHubClientTests {

	@Autowired
	private GitHubClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void requestsAndDeserializesGitHubUser() {
		server.expect(once(), requestTo("https://api.github.test/users/octocat"))
				.andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
				.andExpect(header(HttpHeaders.USER_AGENT, "developer-activity"))
				.andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
				.andRespond(withSuccess("""
						{
						  "login": "octocat",
						  "name": "The Octocat",
						  "html_url": "https://github.com/octocat",
						  "avatar_url": "https://avatars.githubusercontent.com/u/583231",
						  "public_repos": 8,
						  "followers": 17905
						}
						""", MediaType.APPLICATION_JSON));

		GitHubUserResponse user = client.getUser("octocat");

		assertThat(user).isEqualTo(new GitHubUserResponse(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));
		server.verify();
	}

	@Test
	void requestsAndDeserializesRepositories() {
		server.expect(once(), requestTo(
						"https://api.github.test/users/octocat/repos?page=2&per_page=30&sort=updated&direction=desc"
				))
				.andRespond(withSuccess("""
						[
						  {
						    "name": "Spoon-Knife",
						    "description": "This repo is for demonstration purposes only.",
						    "language": "HTML",
						    "stargazers_count": 13,
						    "forks_count": 15,
						    "html_url": "https://github.com/octocat/Spoon-Knife",
						    "updated_at": "2026-08-01T12:30:00Z"
						  }
						]
						""", MediaType.APPLICATION_JSON));

		List<GitHubRepositoryResponse> repositories =
				client.getRepositories("octocat", 2, 30, "updated", "desc");

		assertThat(repositories).containsExactly(new GitHubRepositoryResponse(
				"Spoon-Knife",
				"This repo is for demonstration purposes only.",
				"HTML",
				13,
				15,
				"https://github.com/octocat/Spoon-Knife",
				Instant.parse("2026-08-01T12:30:00Z")
		));
		server.verify();
	}

	@Test
	void requestsAndDeserializesActivities() {
		server.expect(once(), requestTo(
						"https://api.github.test/users/octocat/events?page=1&per_page=20"
				))
				.andRespond(withSuccess("""
						[
						  {
						    "id": "1",
						    "type": "PushEvent",
						    "repo": {"name": "octocat/Hello-World"},
						    "created_at": "2026-08-15T12:30:00Z"
						  }
						]
						""", MediaType.APPLICATION_JSON));

		List<GitHubEventResponse> activities = client.getEvents("octocat", 1, 20);

		assertThat(activities).containsExactly(new GitHubEventResponse(
				"1", "PushEvent", new GitHubEventRepository("octocat/Hello-World"),
				Instant.parse("2026-08-15T12:30:00Z")
		));
		server.verify();
	}
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.http.serviceclient.github.base-url=https://api.github.test",
		"github.api.token=github-token"
})
@AutoConfigureMockRestServiceServer
class GitHubClientTokenTests {

	@Autowired
	private GitHubClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void sendsBearerAuthorizationWhenTokenIsConfigured() {
		server.expect(once(), requestTo("https://api.github.test/users/octocat"))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer github-token"))
				.andRespond(withSuccess("""
						{
						  "login": "octocat",
						  "name": "The Octocat",
						  "html_url": "https://github.com/octocat",
						  "avatar_url": "https://avatars.githubusercontent.com/u/583231",
						  "public_repos": 8,
						  "followers": 17905
						}
						""", MediaType.APPLICATION_JSON));

		client.getUser("octocat");

		server.verify();
	}
}
