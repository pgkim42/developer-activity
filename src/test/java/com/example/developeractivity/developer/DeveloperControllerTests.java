package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
	void rejectsInvalidGitHubUsernameBeforeCallingService() throws Exception {
		mockMvc.perform(get("/developers/{username}", "-invalid-"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));

		verifyNoInteractions(developerService);
	}
}
