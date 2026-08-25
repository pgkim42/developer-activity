package com.example.developeractivity.developer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "github", types = GitHubClient.class)
class GitHubClientConfig {

	@Bean
	RestClientHttpServiceGroupConfigurer githubClientConfigurer(
			@Value("${github.api.token:}") String token
	) {
		return groups -> groups.filterByName("github").forEachClient((group, builder) -> {
			builder.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json");
			builder.defaultHeader(HttpHeaders.USER_AGENT, "developer-activity");
			if (StringUtils.hasText(token)) {
				builder.defaultHeaders(headers -> headers.setBearerAuth(token));
			}
		});
	}
}
