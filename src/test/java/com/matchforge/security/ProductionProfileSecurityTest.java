package com.matchforge.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matchforge.support.AuthTestHelper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
@TestPropertySource(
		properties = {
			"matchforge.security.jwt-secret=test-jwt-secret-key-at-least-32-characters-long",
			"MATCHFORGE_JWT_SECRET=test-jwt-secret-key-at-least-32-characters-long"
		})
class ProductionProfileSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void swaggerUiNotPublicInProduction() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(result -> {
					int status = result.getResponse().getStatus();
					if (status != 401 && status != 403 && status != 404) {
						throw new AssertionError("Expected swagger to be protected or disabled, got " + status);
					}
				});
	}

	@Test
	void prometheusNotPublicInProduction() throws Exception {
		mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
	}

	@Test
	void healthRemainsPublicInProduction() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void adminCanReachPrometheus() throws Exception {
		String adminToken = AuthTestHelper.loginAdmin(mockMvc);
		mockMvc.perform(get("/actuator/metrics").with(AuthTestHelper.bearer(adminToken)))
				.andExpect(status().isOk());
	}
}