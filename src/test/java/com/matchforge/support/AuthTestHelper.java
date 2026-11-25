package com.matchforge.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class AuthTestHelper {

	public static final String DEFAULT_PASSWORD = "StrongPassword-Example-1";
	private static final String TEST_ADMIN_USERNAME = "testadmin";
	private static final String TEST_ADMIN_PASSWORD = "TestAdminPassphrase-1";

	private AuthTestHelper() {
	}

	public static RegisteredPlayer registerPlayer(MockMvc mockMvc, String username) throws Exception {
		return registerPlayer(mockMvc, username, DEFAULT_PASSWORD, null);
	}

	public static RegisteredPlayer registerPlayer(MockMvc mockMvc, String username, String password, Integer skillRating)
			throws Exception {
		String skill = skillRating == null ? "" : ",\"skillRating\":" + skillRating;
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"%s"%s}
								"""
								.formatted(username, password, skill)))
				.andExpect(status().isCreated())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new RegisteredPlayer(
				JsonPath.read(body, "$.playerId"),
				JsonPath.read(body, "$.accountId"),
				JsonPath.read(body, "$.accessToken"));
	}

	public static String loginPlayer(MockMvc mockMvc, String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"%s"}
								"""
								.formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
	}

	public static String loginAdmin(MockMvc mockMvc) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"%s"}
								"""
								.formatted(TEST_ADMIN_USERNAME, TEST_ADMIN_PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
	}

	public static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader("Authorization", "Bearer " + token);
			return request;
		};
	}

	public record RegisteredPlayer(String playerId, String accountId, String token) {
	}
}