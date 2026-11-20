package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
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
		todo.setStatus(TodoStatus.IN_PROGRESS);
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
				.param("status", "IN_PROGRESS"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/confirm"))
				.andExpect(model().attribute("title", "Sample task"))
				.andExpect(model().attribute("description", "Sample description"))
				.andExpect(model().attribute("startDate", "2026-11-20"))
				.andExpect(model().attribute("dueDate", "2026-11-20"))
				.andExpect(model().attribute("confirmedStatus", TodoStatus.IN_PROGRESS));
	}

	@Test
	void completeReturnsCompleteView() throws Exception {
		mockMvc.perform(get("/todo/complete"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/complete"));
	}

	@Test
	void completeSavesSelectedStatus() throws Exception {
		String title = "Task created in progress";
		try {
			mockMvc.perform(post("/todo/complete").with(csrf())
					.param("title", title)
					.param("status", "IN_PROGRESS"))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/todo"));
			List<Todo> savedTodos = todoMapper.findPage(title, TodoStatus.IN_PROGRESS, null, null, 10, 0);
			assertEquals(1, savedTodos.size());
			assertEquals(TodoStatus.IN_PROGRESS, savedTodos.get(0).getStatus());
		} finally {
			todoMapper.findPage(title, null, null, null, 10, 0)
					.forEach(todo -> todoMapper.deleteById(todo.getId()));
		}
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
		todo.setDisplayOrder(100);
		todoMapper.insert(todo);

		try {
			mockMvc.perform(post("/todo/{id}/update", todo.getId()).with(csrf())
					.param("title", "After update")
					.param("status", "COMPLETED"))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/todo"))
					.andExpect(flash().attribute("successMessage", "更新が完了しました"));
			Todo updatedTodo = todoMapper.findById(todo.getId());
			assertEquals("After update", updatedTodo.getTitle());
			assertEquals(TodoStatus.COMPLETED, updatedTodo.getStatus());
		} finally {
			todoMapper.deleteById(todo.getId());
		}
	}

	@Test
	void updateStatusChangesOnlyStatusAndRedirectsToTodoList() throws Exception {
		Todo todo = new Todo();
		todo.setTitle("Status update target");
		todo.setDisplayOrder(100);
		todo.setCategoryId(1L);
		todo.setAssigneeId(1L);
		todo.setStartDate(LocalDate.of(2026, 11, 1));
		todo.setDueDate(LocalDate.of(2026, 11, 15));
		todoMapper.insert(todo);

		try {
			for (TodoStatus status : List.of(TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED, TodoStatus.NOT_STARTED)) {
				mockMvc.perform(post("/todo/{id}/status", todo.getId()).with(csrf())
						.param("status", status.name()))
						.andExpect(status().is3xxRedirection())
						.andExpect(redirectedUrl("/todo"));
				assertEquals(status, todoMapper.findById(todo.getId()).getStatus());
			}
			Todo updatedTodo = todoMapper.findById(todo.getId());
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
