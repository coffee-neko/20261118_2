package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoProgress;
import com.example.demo.model.TodoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	@Test
	void registrationKeepsParentAndSearchShowsChildWithItsParentTitle() throws Exception {
		TodoProgress initialProgress = todoMapper.findQuestProgress();
		Todo parent = new Todo();
		parent.setTitle("Parent registration target");
		parent.setDisplayOrder(100);
		parent.setStatus(TodoStatus.COMPLETED);
		todoMapper.insert(parent);
		String title = "Child registration target";
		try {
			mockMvc.perform(post("/todo/confirm").with(csrf()).param("title", title)
					.param("description", "").param("parentId", parent.getId().toString()))
					.andExpect(view().name("todo/confirm"))
					.andExpect(model().attribute("parent", parent));
			mockMvc.perform(post("/todo/complete").with(csrf()).param("title", title)
					.param("parentId", parent.getId().toString()))
					.andExpect(redirectedUrl("/todo"));
			List<Todo> results = todoMapper.findPage(title, null, null, null, 10, 0);
			assertEquals(1, results.size());
			Todo child = results.get(0);
			assertEquals(parent.getId(), child.getParentId());
			assertEquals(parent.getTitle(), child.getParentTitle());
			Todo parentRow = todoMapper.findPage(parent.getTitle(), null, null, null, 10, 0).get(0);
			assertEquals(1L, parentRow.getChildCount());
			assertEquals(0L, parentRow.getCompletedChildCount());
			assertEquals(new TodoProgress(initialProgress.total() + 1, initialProgress.completed()),
					todoMapper.findQuestProgress());
			mockMvc.perform(get("/todo").param("keyword", title))
					.andExpect(status().isOk())
					.andExpect(model().attribute("todos", results))
					.andExpect(model().attribute("totalCount", 1L));

			mockMvc.perform(post("/todo/{id}/update", child.getId()).with(csrf())
					.param("title", title).param("parentId", ""))
					.andExpect(redirectedUrl("/todo"));
			assertNull(todoMapper.findById(child.getId()).getParentId());
		} finally {
			todoMapper.findPage(title, null, null, null, 10, 0)
					.forEach(todo -> todoMapper.deleteById(todo.getId()));
			todoMapper.deleteById(parent.getId());
		}
	}

	@Test
	void parentWithChildrenCannotBeDeletedOrMovedAndChildCannotBeAParent() throws Exception {
		Todo parent = new Todo();
		parent.setTitle("Parent deletion target");
		parent.setDisplayOrder(100);
		todoMapper.insert(parent);
		Todo child = new Todo();
		child.setTitle("Child deletion target");
		child.setDisplayOrder(101);
		child.setParentId(parent.getId());
		todoMapper.insert(child);
		Todo other = new Todo();
		other.setTitle("Independent task");
		other.setDisplayOrder(102);
		todoMapper.insert(other);
		try {
			mockMvc.perform(post("/todo/{id}/delete", parent.getId()).with(csrf()))
					.andExpect(redirectedUrl("/todo"))
					.andExpect(flash().attributeExists("errorMessage"));
			for (Long invalidParentId : List.of(parent.getId(), child.getId(), other.getId())) {
				mockMvc.perform(post("/todo/{id}/update", parent.getId()).with(csrf())
						.param("title", parent.getTitle()).param("parentId", invalidParentId.toString()))
						.andExpect(view().name("todo/edit"))
						.andExpect(model().attributeExists("errorMessage"));
			}
			mockMvc.perform(post("/todo/complete").with(csrf()).param("title", "Invalid grandchild")
					.param("parentId", child.getId().toString()))
					.andExpect(view().name("todo/form"))
					.andExpect(model().attributeExists("errorMessage"));
			assertNull(todoMapper.findById(parent.getId()).getParentId());
			assertEquals(parent.getId(), todoMapper.findById(child.getId()).getParentId());
		} finally {
			todoMapper.deleteById(child.getId());
			todoMapper.deleteById(parent.getId());
			todoMapper.deleteById(other.getId());
		}
	}
}
