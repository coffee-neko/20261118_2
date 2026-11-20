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
		List<Todo> todos = todoMapper.findPage("", null, null, null, 10, 0);
		List<Long> reorderedIds = new ArrayList<>(todos.stream().map(Todo::getId).toList());
		Collections.reverse(reorderedIds);

		mockMvc.perform(put("/api/todo/reorder").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(1, reorderedIds)))
				.andExpect(status().isNoContent());

		List<Long> savedIds = todoMapper.findPage("", null, null, null, 10, 0).stream().map(Todo::getId).toList();
		assertEquals(reorderedIds, savedIds);
	}

	@Test
	void reorderRejectsDuplicateIds() throws Exception {
		List<Todo> todos = todoMapper.findPage("", null, null, null, 10, 0);
		List<Long> duplicatedIds = todos.stream().map(Todo::getId).collect(Collectors.toList());
		duplicatedIds.set(1, duplicatedIds.get(0));

		mockMvc.perform(put("/api/todo/reorder").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(1, duplicatedIds)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reorderKeepsChildrenUnderTheirParentAndRejectsMixedGroups() throws Exception {
		Todo parent = insertTask("Reorder parent", -3, null);
		Todo first = insertTask("First child", -2, parent.getId());
		Todo second = insertTask("Second child", -1, parent.getId());
		try {
			mockMvc.perform(put("/api/todo/reorder").with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(1, parent.getId(), List.of(second.getId(), first.getId()))))
					.andExpect(status().isNoContent());
			assertEquals(List.of(parent.getId(), second.getId(), first.getId()),
					todoMapper.findPage("", null, null, null, 3, 0).stream().map(Todo::getId).toList());

			mockMvc.perform(put("/api/todo/reorder").with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(1, parent.getId(), List.of(parent.getId(), first.getId()))))
					.andExpect(status().isBadRequest());
			mockMvc.perform(put("/api/todo/reorder").with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(2, parent.getId(), List.of(second.getId(), first.getId()))))
					.andExpect(status().isBadRequest());
			assertEquals(parent.getId(), todoMapper.findById(first.getId()).getParentId());
			assertEquals(parent.getId(), todoMapper.findById(second.getId()).getParentId());
		} finally {
			todoMapper.deleteById(first.getId());
			todoMapper.deleteById(second.getId());
			todoMapper.deleteById(parent.getId());
		}
	}

	private Todo insertTask(String title, int displayOrder, Long parentId) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setDisplayOrder(displayOrder);
		todo.setParentId(parentId);
		todoMapper.insert(todo);
		return todo;
	}

	private String requestBody(int page, List<Long> todoIds) {
		return requestBody(page, null, todoIds);
	}

	private String requestBody(int page, Long parentId, List<Long> todoIds) {
		return "{\"page\":" + page + ",\"parentId\":" + parentId + ",\"todoIds\":["
				+ todoIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]}";
	}
}
