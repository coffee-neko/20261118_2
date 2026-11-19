package com.example.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TodoReorderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void reorderSavesTheOrderWithinTheCurrentPage() throws Exception {
		List<Todo> todos = todoMapper.findPage("", null, null, 10, 0);
		List<Long> reorderedIds = new ArrayList<>(todos.stream().map(Todo::getId).toList());
		Collections.reverse(reorderedIds);

		mockMvc.perform(put("/api/todo/reorder").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(1, reorderedIds)))
				.andExpect(status().isNoContent());

		List<Long> savedIds = todoMapper.findPage("", null, null, 10, 0).stream().map(Todo::getId).toList();
		assertEquals(reorderedIds, savedIds);
	}

	@Test
	void reorderRejectsDuplicateIds() throws Exception {
		List<Todo> todos = todoMapper.findPage("", null, null, 10, 0);
		List<Long> duplicatedIds = todos.stream().map(Todo::getId).collect(Collectors.toList());
		duplicatedIds.set(1, duplicatedIds.get(0));

		mockMvc.perform(put("/api/todo/reorder").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(1, duplicatedIds)))
				.andExpect(status().isBadRequest());
	}

	private String requestBody(int page, List<Long> todoIds) {
		return "{\"page\":" + page + ",\"todoIds\":["
				+ todoIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]}";
	}
}
