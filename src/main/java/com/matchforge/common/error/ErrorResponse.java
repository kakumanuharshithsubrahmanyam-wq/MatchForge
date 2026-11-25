package com.matchforge.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

	public static ErrorResponse of(int status, String error, String message, String path) {
		return new ErrorResponse(Instant.now(), status, error, message, path, null);
	}

	public static ErrorResponse withFieldErrors(
			int status, String error, String message, String path, List<FieldError> fieldErrors) {
		return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
	}
}