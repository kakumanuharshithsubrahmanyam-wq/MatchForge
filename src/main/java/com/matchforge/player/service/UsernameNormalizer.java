package com.matchforge.player.service;

public final class UsernameNormalizer {

	private UsernameNormalizer() {
	}

	public static String normalize(String username) {
		if (username == null) {
			return null;
		}
		return username.strip().toLowerCase();
	}
}