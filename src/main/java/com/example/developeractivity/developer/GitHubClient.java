package com.example.developeractivity.developer;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.service.annotation.GetExchange;

import java.util.List;

interface GitHubClient {

	@GetExchange("/users/{username}")
	GitHubUserResponse getUser(@PathVariable String username);

	@GetExchange("/users/{username}/repos")
	List<GitHubRepositoryResponse> getRepositories(
			@PathVariable String username,
			@RequestParam int page,
			@RequestParam("per_page") int size,
			@RequestParam String sort,
			@RequestParam String direction
	);

	@GetExchange("/users/{username}/events")
	List<GitHubEventResponse> getEvents(
			@PathVariable String username,
			@RequestParam int page,
			@RequestParam("per_page") int size
	);
}
