package com.example.developeractivity.developer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/developers")
@RequiredArgsConstructor
class DeveloperController {

	private static final String GITHUB_USERNAME_PATTERN =
			"[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?";

	private final DeveloperService developerService;

	@GetMapping("/{username}")
	ResponseEntity<DeveloperProfile> getProfile(
			@PathVariable
			@Pattern(regexp = GITHUB_USERNAME_PATTERN, message = "must be a valid GitHub username")
			String username,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
	) {
		DeveloperProfile profile = developerService.getProfile(username);
		return conditionalResponse(profile, ifNoneMatch);
	}

	@GetMapping("/{username}/repositories")
	ResponseEntity<List<DeveloperRepository>> getRepositories(
			@PathVariable
			@Pattern(regexp = GITHUB_USERNAME_PATTERN, message = "must be a valid GitHub username")
			String username,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
	) {
		List<DeveloperRepository> repositories = developerService.getRepositories(username, page, size);
		return conditionalResponse(repositories, ifNoneMatch);
	}

	@GetMapping("/{username}/activities")
	ResponseEntity<List<DeveloperActivity>> getActivities(
			@PathVariable
			@Pattern(regexp = GITHUB_USERNAME_PATTERN, message = "must be a valid GitHub username")
			String username,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
	) {
		List<DeveloperActivity> activities = developerService.getActivities(username, page, size);
		return conditionalResponse(activities, ifNoneMatch);
	}

	@GetMapping("/{username}/activity-summary")
	ResponseEntity<DeveloperActivitySummary> getActivitySummary(
			@PathVariable
			@Pattern(regexp = GITHUB_USERNAME_PATTERN, message = "must be a valid GitHub username")
			String username,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
	) {
		DeveloperActivitySummary summary = developerService.getActivitySummary(username);
		return conditionalResponse(summary, ifNoneMatch);
	}

	private <T> ResponseEntity<T> conditionalResponse(T body, String ifNoneMatch) {
		String etag = "\"" + Integer.toHexString(body.hashCode()) + "\"";
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).body(body);
	}
}
