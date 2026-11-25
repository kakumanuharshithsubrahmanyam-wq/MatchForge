package com.matchforge.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
class TestExceptionController {

	@GetMapping("/not-found")
	void notFound() {
		throw new ResourceNotFoundException("Player not found");
	}

	@GetMapping("/conflict")
	void conflict() {
		throw new ConflictException("Display name already taken");
	}

	@GetMapping("/unexpected")
	void unexpected() {
		throw new IllegalStateException("boom-internal-detail");
	}

	@PostMapping("/validate")
	void validate(@Valid @RequestBody ValidateNameRequest request) {
		// No-op: validation runs before this method.
	}

	record ValidateNameRequest(@NotBlank String name) {
	}
}