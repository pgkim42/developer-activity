package com.example.developeractivity.developer;

final class DeveloperNotFoundException extends RuntimeException {

	DeveloperNotFoundException(String username) {
		super("Developer '%s' was not found".formatted(username));
	}
}
