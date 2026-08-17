package com.example.developeractivity.developer;

import java.util.List;
import java.util.Map;

record DeveloperActivitySummary(
        int eventCount,
        Map<String, Integer> eventTypeCounts,
        List<RepositoryActivityCount> repositories
) {
}

record RepositoryActivityCount(String repository, int activityCount) {
}
