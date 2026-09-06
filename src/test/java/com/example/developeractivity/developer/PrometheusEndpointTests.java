package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@AutoConfigureMetrics
class PrometheusEndpointTests {

	@Autowired
	private RestTestClient restTestClient;

	@Autowired
	private DeveloperCache cache;

	@Autowired
	private DeveloperService developerService;

	@Test
	void exposesPrometheusScrapeAfterACacheHit() {
		cache.put("profile:octocat", new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));
		developerService.getProfile("octocat");

		restTestClient.get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.value(body -> assertThat(body).contains("developer_cache_hits"));
	}

	@Test
	void keepsJsonCacheHitMeter() {
		cache.put("profile:octocat", new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));
		developerService.getProfile("octocat");

		restTestClient.get().uri("/actuator/metrics/developer.cache.hits")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.value(body -> assertThat(body).contains("developer.cache.hits"));
	}
}
