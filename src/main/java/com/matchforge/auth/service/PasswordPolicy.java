package com.matchforge.auth.service;

import com.matchforge.common.error.ConflictException;

final class PasswordPolicy {

	private static final int MIN_LENGTH = 12;

	private PasswordPolicy() {
	}

	static void validate(String password) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw new ConflictException("Password must be at least " + MIN_LENGTH + " characters");
		}
	}
}