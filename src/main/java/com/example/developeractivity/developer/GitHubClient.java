package com.example.developeractivity.developer;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

interface GitHubClient {

	@GetExchange("/users/{username}")
	GitHubUserResponse getUser(@PathVariable String username);
}
