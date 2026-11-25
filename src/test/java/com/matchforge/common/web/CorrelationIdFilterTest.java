package com.matchforge.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generatesCorrelationIdWhenHeaderMissing() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(header().exists(CorrelationIdFilter.HEADER));
	}

	@Test
	void propagatesValidCorrelationId() throws Exception {
		String correlationId = "abc-def-1234567890";
		mockMvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER, correlationId))
				.andExpect(status().isOk())
				.andExpect(header().string(CorrelationIdFilter.HEADER, correlationId));
	}

	@Test
	void replacesInvalidCorrelationId() throws Exception {
		mockMvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER, "bad id!"))
				.andExpect(status().isOk())
				.andExpect(header().exists(CorrelationIdFilter.HEADER))
				.andExpect(header().string(CorrelationIdFilter.HEADER, org.hamcrest.Matchers.not("bad id!")));
	}
}