package com.example.developeractivity.developer;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/developers")
@RequiredArgsConstructor
class DeveloperController {

	private static final String GITHUB_USERNAME_PATTERN =
			"[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?";

	private final DeveloperService developerService;

	@GetMapping("/{username}")
	DeveloperProfile getProfile(
			@PathVariable
			@Pattern(regexp = GITHUB_USERNAME_PATTERN, message = "must be a valid GitHub username")
			String username
	) {
		return developerService.getProfile(username);
	}
}
