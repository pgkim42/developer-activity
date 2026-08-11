package com.example.developeractivity.developer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
class DeveloperService {

	private final GitHubClient gitHubClient;

	DeveloperProfile getProfile(String username) {
		try {
			return DeveloperProfile.from(gitHubClient.getUser(username));
		} catch (HttpClientErrorException.NotFound exception) {
			throw new DeveloperNotFoundException(username);
		} catch (RestClientException exception) {
			throw new GitHubUnavailableException(exception);
		}
	}
}
