package com.example.demo;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoBulkControllerTest")
@AutoConfigureMockMvc
@WithMockUser
class TodoBulkControllerTest {
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private TodoService todoService;
	@Autowired
	private TodoMapper todoMapper;

	@Test
	void changesOnlySelectedTasksAndRejectsIdsOutsideTheFilteredPage() throws Exception {
		Todo parent = create("一括変更の親", null);
		Todo child = create("一括変更の子", parent.getId());
		Todo independent = create("一括変更の独立", null);
		mockMvc.perform(post("/todo/bulk/status").with(csrf()).param("page", "1").param("keyword", "一括変更")
				.param("status", "NOT_STARTED").param("newStatus", "COMPLETED")
				.param("ids", parent.getId().toString(), independent.getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "2件のステータスを「完了」に変更しました。"));
		assertEquals(TodoStatus.COMPLETED, todoMapper.findById(parent.getId()).getStatus());
		assertEquals(TodoStatus.COMPLETED, todoMapper.findById(independent.getId()).getStatus());
		assertEquals(TodoStatus.NOT_STARTED, todoMapper.findById(child.getId()).getStatus());

		mockMvc.perform(post("/todo/bulk/status").with(csrf()).param("keyword", "一括変更の子")
				.param("newStatus", "IN_PROGRESS").param("ids", child.getId().toString(), parent.getId().toString()))
				.andExpect(flash().attribute("errorMessage", containsString("変更は0件")));
		assertEquals(TodoStatus.NOT_STARTED, todoMapper.findById(child.getId()).getStatus());
		assertEquals(TodoStatus.COMPLETED, todoMapper.findById(parent.getId()).getStatus());
	}

	@Test
	void deletionConfirmationIsSessionBoundAndCanOnlyBeUsedOnce() throws Exception {
		Todo parent = create("親子削除の親", null);
		Todo child = create("親子削除の子", parent.getId());
		MockHttpSession session = new MockHttpSession();
		var preview = mockMvc.perform(post("/todo/bulk/delete/preview").session(session).with(csrf())
				.param("keyword", "親子削除").param("ids", parent.getId().toString(), child.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("confirmationId"))
				.andExpect(content().string(containsString("次の2件")))
				.andExpect(content().string(containsString("子タスク（親：親子削除の親）")))
				.andReturn();
		String confirmationId = (String) preview.getModelAndView().getModel().get("confirmationId");
		assertNotNull(todoMapper.findById(child.getId()));
		assertNotNull(todoMapper.findById(parent.getId()));
		mockMvc.perform(post("/todo/bulk/delete/confirm").session(new MockHttpSession()).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(redirectedUrl("/todo"))
				.andExpect(flash().attribute("errorMessage", containsString("削除は0件")));
		mockMvc.perform(post("/todo/bulk/delete/confirm").session(session).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(flash().attribute("successMessage", "2件のタスクを削除しました。"));
		assertNull(todoMapper.findById(child.getId()));
		assertNull(todoMapper.findById(parent.getId()));
		mockMvc.perform(post("/todo/bulk/delete/confirm").session(session).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(flash().attribute("errorMessage", containsString("使用済み")));
	}

	@Test
	void rechecksChildrenHiddenByFiltersBeforeDeletingAnything() throws Exception {
		Todo parent = create("未選択検証の親", null);
		Todo independent = create("未選択検証の独立", null);
		MockHttpSession session = new MockHttpSession();
		var preview = mockMvc.perform(post("/todo/bulk/delete/preview").session(session).with(csrf())
				.param("keyword", "未選択検証").param("ids", independent.getId().toString(), parent.getId().toString()))
				.andExpect(model().attributeExists("confirmationId"))
				.andReturn();
		String confirmationId = (String) preview.getModelAndView().getModel().get("confirmationId");
		Todo child = create("絞り込みに出ない子", parent.getId());

		mockMvc.perform(post("/todo/bulk/delete/confirm").session(session).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(flash().attribute("errorMessage", containsString("選択されていない子タスク")));
		mockMvc.perform(post("/todo/bulk/delete/preview").session(session).with(csrf())
				.param("keyword", "未選択検証").param("ids", independent.getId().toString(), parent.getId().toString()))
				.andExpect(flash().attribute("errorMessage", containsString("削除は0件")));
		assertNotNull(todoMapper.findById(parent.getId()));
		assertNotNull(todoMapper.findById(child.getId()));
		assertNotNull(todoMapper.findById(independent.getId()));
	}

	@Test
	void bulkPostsRequireCsrf() throws Exception {
		for (String path : new String[] {"/status", "/delete/preview", "/delete/confirm"}) {
			mockMvc.perform(post("/todo/bulk" + path)).andExpect(status().isForbidden());
		}
	}

	private Todo create(String title, Long parentId) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setParentId(parentId);
		todoService.create(todo);
		return todo;
	}
}
