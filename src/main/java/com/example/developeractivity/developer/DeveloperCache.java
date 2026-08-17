package com.example.developeractivity.developer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
class DeveloperCache {

    private final Duration freshFor;
    private final Duration staleFor;
    private final Cache<String, Entry> entries = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    DeveloperCache(
            @Value("${developer.cache.fresh-for:5m}") Duration freshFor,
            @Value("${developer.cache.stale-for:15m}") Duration staleFor
    ) {
        this.freshFor = freshFor;
        this.staleFor = staleFor;
    }

    Object fresh(String key) {
        Entry entry = entries.getIfPresent(key);
        return entry != null && entry.freshUntil().isAfter(Instant.now()) ? entry.value() : null;
    }

    Object stale(String key) {
        Entry entry = entries.getIfPresent(key);
        return entry != null && entry.staleUntil().isAfter(Instant.now()) ? entry.value() : null;
    }

    void put(String key, Object value) {
        Instant now = Instant.now();
        entries.put(key, new Entry(value, now.plus(freshFor), now.plus(freshFor).plus(staleFor)));
    }

    private record Entry(Object value, Instant freshUntil, Instant staleUntil) {
    }
}
