package com.example.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.example.demo.model.Todo;
import com.example.demo.model.TodoDashboard;
import com.example.demo.model.TodoProgress;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoDashboardTest")
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class TodoDashboardTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

	@Autowired
	private TodoService todoService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void clearSeedTodos() {
		jdbcTemplate.update("DELETE FROM todos WHERE parent_id IS NOT NULL");
		jdbcTemplate.update("DELETE FROM todos");
	}

	@Test
	void dashboardCountsOnlyTasksWithoutChildrenAndIncludesUnassignedGroups() {
		var assignee = todoService.findAllAssignees().get(0);
		var category = todoService.findAllCategories().get(0);
		Todo parent = create("年調の親", TodoStatus.IN_PROGRESS, TODAY.minusDays(1), null, category.getId(), assignee.getId());
		Todo lastDay = create("6日後の子", TodoStatus.IN_PROGRESS, TODAY.plusDays(6), parent.getId(), null, null);
		Todo today = create("今日の子", TodoStatus.NOT_STARTED, TODAY, parent.getId(), category.getId(), assignee.getId());
		create("昨日が期限", TodoStatus.NOT_STARTED, TODAY.minusDays(1), null, null, null);
		create("昨日が期限の完了済み", TodoStatus.COMPLETED, TODAY.minusDays(1), null, category.getId(), assignee.getId());
		create("今日が期限の完了済み", TodoStatus.COMPLETED, TODAY, null, null, null);
		create("期限なし", TodoStatus.NOT_STARTED, null, null, null, null);
		create("7日後が期限", TodoStatus.IN_PROGRESS, TODAY.plusDays(7), null, category.getId(), assignee.getId());

		TodoDashboard dashboard = todoService.loadDashboard(TODAY);
		assertEquals(TODAY.plusDays(6), dashboard.lastDay());
		assertEquals(new TodoDashboard.Summary(7, 3, 2, 2, 1), dashboard.summary());
		assertEquals(new TodoProgress(7, 2), todoService.findQuestProgress());
		assertEquals(29, dashboard.summary().percentage());
		assertEquals(List.of(today.getId(), lastDay.getId()), dashboard.upcoming().stream().map(Todo::getId).toList());
		assertEquals(List.of(new TodoDashboard.Group(assignee.getName(), 3, 1),
				new TodoDashboard.Group(null, 4, 1)), dashboard.byAssignee());
		assertEquals(List.of(new TodoDashboard.Group(category.getName(), 3, 1),
				new TodoDashboard.Group(null, 4, 1)), dashboard.byCategory());
		assertEquals(33, dashboard.byAssignee().get(0).percentage());
		assertEquals(25, dashboard.byCategory().get(1).percentage());
	}

	@Test
	void emptyDashboardReturnsZeroCountsAndEmptyGroups() throws Exception {
		TodoDashboard dashboard = todoService.loadDashboard(TODAY);
		assertEquals(new TodoDashboard.Summary(0, 0, 0, 0, 0), dashboard.summary());
		assertEquals(0, dashboard.summary().percentage());
		assertTrue(dashboard.upcoming().isEmpty());
		assertTrue(dashboard.byAssignee().isEmpty());
		assertTrue(dashboard.byCategory().isEmpty());
		mockMvc.perform(get("/todo/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("この期間に期限を迎える未完了タスクはありません。")));
	}

	@Test
	void businessDateRollsOverAtMidnightInTokyo() {
		assertEquals(TODAY.minusDays(1), TodoController.businessDate(Instant.parse("2026-11-22T14:59:59Z")));
		assertEquals(TODAY, TodoController.businessDate(Instant.parse("2026-11-22T15:00:00Z")));
	}

	@Test
	void dashboardIgnoresListFiltersAndLinksToReadOnlyDetails() throws Exception {
		Todo upcomingTask = create("画面で確認するタスク", TodoStatus.NOT_STARTED,
				TodoController.businessDate(Instant.now()).plusDays(1), null, null, null);
		mockMvc.perform(get("/todo/dashboard").param("keyword", "一致しない検索語").param("status", "COMPLETED"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/dashboard"))
				.andExpect(content().string(containsString("href=\"/todo/" + upcomingTask.getId() + "\"")))
				.andExpect(content().string(containsString("担当者未設定")))
				.andExpect(content().string(containsString("カテゴリ未設定")));
		mockMvc.perform(get("/todo/{id}", upcomingTask.getId()))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/detail"))
				.andExpect(content().string(containsString(upcomingTask.getTitle())));
		mockMvc.perform(get("/todo/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
	}

	private Todo create(String title, TodoStatus status, LocalDate dueDate, Long parentId, Long categoryId, Long assigneeId) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setStatus(status);
		todo.setDueDate(dueDate);
		todo.setParentId(parentId);
		todo.setCategoryId(categoryId);
		todo.setAssigneeId(assigneeId);
		todoService.create(todo);
		return todo;
	}
}
