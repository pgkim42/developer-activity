package com.example.developeractivity.developer;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Duration;
import java.time.Instant;

@RestControllerAdvice(assignableTypes = DeveloperController.class)
class DeveloperExceptionHandler {

	@ExceptionHandler(DeveloperNotFoundException.class)
	ProblemDetail handleNotFound(DeveloperNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Developer not found", exception.getMessage());
	}

	@ExceptionHandler(GitHubTimeoutException.class)
	ProblemDetail handleTimeout(GitHubTimeoutException exception) {
		return problem(HttpStatus.GATEWAY_TIMEOUT, "Upstream service timed out", exception.getMessage());
	}
	@ExceptionHandler(GitHubUnavailableException.class)
	ProblemDetail handleUnavailable(GitHubUnavailableException exception) {
		return problem(HttpStatus.BAD_GATEWAY, "Upstream service unavailable", exception.getMessage());
	}

	@ExceptionHandler(GitHubRateLimitException.class)
	ProblemDetail handleRateLimit(GitHubRateLimitException exception) {
		ProblemDetail problem = problem(
				HttpStatus.TOO_MANY_REQUESTS, "GitHub API rate limit exceeded", exception.getMessage()
		);
		exception.retryAt().ifPresent(retryAt -> {
			long seconds = Math.max(0, Duration.between(Instant.now(), retryAt).toSeconds());
			problem.setProperty("retryAfterSeconds", seconds);
			problem.setProperty("rateLimitReset", retryAt.toString());
		});
		if (exception.remaining() != null) {
			problem.setProperty("rateLimitRemaining", exception.remaining());
		}
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleValidation(ConstraintViolationException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
