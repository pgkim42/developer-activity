package com.example.developeractivity.developer;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DeveloperCacheTests {

	@Test
	void keepsEntryAvailableAsStaleAfterFreshPeriod() {
		DeveloperCache cache = new DeveloperCache(Duration.ZERO, Duration.ofMinutes(1));

		cache.put("profile:octocat", "cached profile");

		assertThat(cache.fresh("profile:octocat")).isNull();
		assertThat(cache.stale("profile:octocat")).isEqualTo("cached profile");
	}

	@Test
	void doesNotReturnExpiredStaleEntry() {
		DeveloperCache cache = new DeveloperCache(Duration.ZERO, Duration.ZERO);

		cache.put("profile:octocat", "cached profile");

		assertThat(cache.stale("profile:octocat")).isNull();
	}
}
