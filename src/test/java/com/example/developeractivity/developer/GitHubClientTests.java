package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubClientTests {

	@Test
	void requestsAndDeserializesGitHubUser() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubClient client = new GitHubClientConfig().githubClient(builder, "https://api.github.test");
		server.expect(once(), requestTo("https://api.github.test/users/octocat"))
				.andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
				.andExpect(header(HttpHeaders.USER_AGENT, "developer-activity"))
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
}
