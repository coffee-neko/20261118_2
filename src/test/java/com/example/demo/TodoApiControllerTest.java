package com.example.demo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoApiControllerTest")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TodoApiControllerTest {
	private static final String TEST_USER = "api-test-user";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void supportsListingReadingCreatingUpdatingAndDeletingTasks() throws Exception {
		mockMvc.perform(get("/api/todos").with(user(TEST_USER).roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		MvcResult created = mockMvc.perform(post("/api/todos").with(user(TEST_USER).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("REST API test task", "NOT_STARTED", "2026-11-01", "2026-11-15")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/todos/")))
				.andExpect(jsonPath("$.title").value("REST API test task"))
				.andReturn();
		long id = idFrom(created);

		mockMvc.perform(get("/api/todos/{id}", id).with(user(TEST_USER).roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.dueDate").value("2026-11-15"));

		mockMvc.perform(put("/api/todos/{id}", id).with(user(TEST_USER).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("REST API updated task", "IN_PROGRESS", "2026-11-02", "2026-11-20")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("REST API updated task"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		mockMvc.perform(delete("/api/todos/{id}", id).with(user(TEST_USER).roles("USER")).with(csrf()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/todos/{id}", id).with(user(TEST_USER).roles("USER")))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnsNotFoundForAnUnknownTask() throws Exception {
		mockMvc.perform(get("/api/todos/{id}", Long.MAX_VALUE).with(user(TEST_USER).roles("USER")))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAnEmptyTitle() throws Exception {
		mockMvc.perform(post("/api/todos").with(user(TEST_USER).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("   ", "NOT_STARTED", null, null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsAnInvalidDate() throws Exception {
		mockMvc.perform(post("/api/todos").with(user(TEST_USER).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("Invalid date task", "NOT_STARTED", "2026-99-99", null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresCsrfForTaskCreation() throws Exception {
		mockMvc.perform(post("/api/todos").with(user(TEST_USER).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("CSRF protected task", "NOT_STARTED", null, null)))
				.andExpect(status().isForbidden());
	}

	private long idFrom(MvcResult result) throws Exception {
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.required("id").longValue();
	}

	private String request(String title, String status, String startDate, String dueDate) {
		return """
				{
				  "title": "%s",
				  "status": "%s",
				  "categoryId": null,
				  "assigneeId": null,
				  "parentId": null,
				  "startDate": %s,
				  "dueDate": %s
				}
				""".formatted(title, status, jsonStringOrNull(startDate), jsonStringOrNull(dueDate));
	}

	private String jsonStringOrNull(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
