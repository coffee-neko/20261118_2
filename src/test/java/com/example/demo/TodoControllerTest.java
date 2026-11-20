package com.example.demo;

import java.time.LocalDate;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TodoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void todoListReturnsListView() throws Exception {
		mockMvc.perform(get("/todo"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/list"))
				.andExpect(model().attributeExists("todos"));
	}

	@Test
	void newTodoReturnsNewView() throws Exception {
		mockMvc.perform(get("/todo/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/form"));
	}

	@Test
	void editReturnsEditView() throws Exception {
		Todo todo = new Todo();
		todo.setTitle("Edit target");
		todo.setCompleted(false);
		todo.setDisplayOrder(100);
		todoMapper.insert(todo);

		try {
			mockMvc.perform(get("/todo/{id}/edit", todo.getId()))
					.andExpect(status().isOk())
					.andExpect(view().name("todo/edit"))
					.andExpect(model().attribute("todo", todo));
		} finally {
			todoMapper.deleteById(todo.getId());
		}
	}

	@Test
	void confirmReturnsConfirmView() throws Exception {
		mockMvc.perform(get("/todo/confirm"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/confirm"));
	}

	@Test
	void confirmReceivesFormParameters() throws Exception {
		mockMvc.perform(post("/todo/confirm").with(csrf())
				.param("title", "Sample task")
				.param("description", "Sample description")
				.param("startDate", "2026-11-20")
				.param("dueDate", "2026-11-20")
				.param("done", "true"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/confirm"))
				.andExpect(model().attribute("title", "Sample task"))
				.andExpect(model().attribute("description", "Sample description"))
				.andExpect(model().attribute("startDate", "2026-11-20"))
				.andExpect(model().attribute("dueDate", "2026-11-20"))
				.andExpect(model().attribute("done", true));
	}

	@Test
	void completeReturnsCompleteView() throws Exception {
		mockMvc.perform(get("/todo/complete"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/complete"));
	}

	@Test
	void completeReceivesTitle() throws Exception {
		mockMvc.perform(post("/todo/complete").with(csrf()).param("title", "Sample task"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/todo"));
	}

	@Test
	void deleteRedirectsToTodoList() throws Exception {
		mockMvc.perform(post("/todo/1/delete").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/todo"));
	}

	@Test
	void updateRedirectsToTodoListWithSuccessMessage() throws Exception {
		Todo todo = new Todo();
		todo.setTitle("Before update");
		todo.setCompleted(false);
		todo.setDisplayOrder(100);
		todoMapper.insert(todo);

		try {
			mockMvc.perform(post("/todo/{id}/update", todo.getId()).with(csrf()).param("title", "After update"))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/todo"))
					.andExpect(flash().attribute("successMessage", "更新が完了しました"));
			assertEquals("After update", todoMapper.findById(todo.getId()).getTitle());
		} finally {
			todoMapper.deleteById(todo.getId());
		}
	}

	@Test
	void toggleCompletedRedirectsToTodoList() throws Exception {
		Todo todo = new Todo();
		todo.setTitle("Toggle target");
		todo.setCompleted(false);
		todo.setDisplayOrder(100);
		todo.setCategoryId(1L);
		todo.setAssigneeId(1L);
		todo.setStartDate(LocalDate.of(2026, 11, 1));
		todo.setDueDate(LocalDate.of(2026, 11, 15));
		todoMapper.insert(todo);

		try {
			mockMvc.perform(post("/todo/{id}/toggle", todo.getId()).with(csrf()))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/todo"));
			Todo updatedTodo = todoMapper.findById(todo.getId());
			assertEquals(true, updatedTodo.getCompleted());
			assertEquals(todo.getTitle(), updatedTodo.getTitle());
			assertEquals(todo.getDisplayOrder(), updatedTodo.getDisplayOrder());
			assertEquals(todo.getCategoryId(), updatedTodo.getCategoryId());
			assertEquals(todo.getAssigneeId(), updatedTodo.getAssigneeId());
			assertEquals(todo.getStartDate(), updatedTodo.getStartDate());
			assertEquals(todo.getDueDate(), updatedTodo.getDueDate());
		} finally {
			todoMapper.deleteById(todo.getId());
		}
	}
}
