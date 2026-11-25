package com.matchforge.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TestExceptionController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void resourceNotFound_returnsStructuredJson() throws Exception {
		mockMvc.perform(get("/test/errors/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Player not found"))
				.andExpect(jsonPath("$.path").value("/test/errors/not-found"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void conflict_returnsStructuredJson() throws Exception {
		mockMvc.perform(get("/test/errors/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("Display name already taken"));
	}

	@Test
	void unexpectedError_doesNotExposeInternalDetails() throws Exception {
		mockMvc.perform(get("/test/errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.message").value("An unexpected error occurred"));

		String body = mockMvc.perform(get("/test/errors/unexpected"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		assertThat(body).doesNotContain("boom-internal-detail");
	}

	@Test
	void validationFailure_returnsFieldErrors() throws Exception {
		mockMvc.perform(post("/test/errors/validate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").exists());
	}

	@Test
	void malformedJson_returnsBadRequestWithoutStackTrace() throws Exception {
		mockMvc.perform(post("/test/errors/validate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed or invalid request"));
	}

	@Test
	void errorResponse_hasConsistentTopLevelFields() throws Exception {
		String body = mockMvc.perform(get("/test/errors/not-found"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(body)
				.contains("\"status\"")
				.contains("\"error\"")
				.contains("\"message\"")
				.contains("\"path\"")
				.contains("\"timestamp\"");
		assertThat(body).doesNotContain("stackTrace");
		assertThat(body).doesNotContain("Trace");
	}
}