package com.example.developeractivity.developer;

import java.time.Instant;

record DeveloperActivity(
        String type,
        String repository,
        String title,
        Instant occurredAt
) {
    static DeveloperActivity from(GitHubEventResponse event) {
        String repository = event.repo() == null ? null : event.repo().name();
        return new DeveloperActivity(
                event.type(),
                repository,
                title(event.type(), repository),
                event.createdAt()
        );
    }

    private static String title(String type, String repository) {
        String target = repository == null ? "repository" : repository;
        return switch (type) {
            case "PushEvent" -> "Pushed commits to " + target;
            case "PullRequestEvent" -> "Updated a pull request in " + target;
            case "IssuesEvent" -> "Updated an issue in " + target;
            case "IssueCommentEvent" -> "Commented on an issue in " + target;
            case "CreateEvent" -> "Created a resource in " + target;
            case "DeleteEvent" -> "Deleted a resource in " + target;
            default -> "Activity in " + target;
        };
    }
}
