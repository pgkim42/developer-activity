package com.example.developeractivity.developer;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeveloperController.class)
class DeveloperExceptionHandler {

	@ExceptionHandler(DeveloperNotFoundException.class)
	ProblemDetail handleNotFound(DeveloperNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Developer not found", exception.getMessage());
	}

	@ExceptionHandler(GitHubUnavailableException.class)
	ProblemDetail handleUnavailable(GitHubUnavailableException exception) {
		return problem(HttpStatus.BAD_GATEWAY, "Upstream service unavailable", exception.getMessage());
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
