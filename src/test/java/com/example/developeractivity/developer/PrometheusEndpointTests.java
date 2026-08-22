package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@AutoConfigureMetrics
class PrometheusEndpointTests {

	@Autowired
	private TestRestTemplate restTemplate;

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

		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("developer_cache_hits");
	}

	@Test
	void keepsJsonCacheHitMeter() {
		cache.put("profile:octocat", new DeveloperProfile(
				"octocat", "The Octocat", "https://github.com/octocat",
				"https://avatars.githubusercontent.com/u/583231", 8, 17_905
		));
		developerService.getProfile("octocat");

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/actuator/metrics/developer.cache.hits", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("developer.cache.hits");
	}
}
