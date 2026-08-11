package com.example.developeractivity.developer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
class GitHubClientConfig {

	@Bean
	GitHubClient githubClient(
			RestClient.Builder builder,
			@Value("${github.api.base-url}") String baseUrl,
			@Value("${github.api.token:}") String token
	) {
		RestClient restClient = builder
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.defaultHeader(HttpHeaders.USER_AGENT, "developer-activity")
				.defaultHeaders(headers -> {
					if (StringUtils.hasText(token)) {
						headers.setBearerAuth(token);
					}
				})
				.build();

		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
				.build()
				.createClient(GitHubClient.class);
	}
}
