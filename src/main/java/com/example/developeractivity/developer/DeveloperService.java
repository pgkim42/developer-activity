package com.example.developeractivity.developer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
class DeveloperService {

	private final GitHubClient gitHubClient;

	DeveloperProfile getProfile(String username) {
		return callGitHub(username, () -> DeveloperProfile.from(gitHubClient.getUser(username)));
	}

	List<DeveloperRepository> getRepositories(String username, int page, int size) {
		return callGitHub(
				username,
				() -> gitHubClient.getRepositories(username, page, size, "updated", "desc")
						.stream()
						.map(DeveloperRepository::from)
						.toList()
		);
	}

	private <T> T callGitHub(String username, Supplier<T> request) {
		try {
			return request.get();
		} catch (HttpClientErrorException.NotFound exception) {
			throw new DeveloperNotFoundException(username);
		} catch (ResourceAccessException exception) {
			if (hasTimeoutCause(exception)) {
				throw new GitHubTimeoutException(exception);
			}
			throw new GitHubUnavailableException(exception);
		} catch (RestClientException exception) {
			throw new GitHubUnavailableException(exception);
		}
	}

	private boolean hasTimeoutCause(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
				return true;
			}
		}
		return false;
	}
}
