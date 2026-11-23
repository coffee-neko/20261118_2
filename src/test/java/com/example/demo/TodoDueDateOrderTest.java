package com.example.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoDueDateOrderTest")
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class TodoDueDateOrderTest {
	private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 23);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private TodoMapper todoMapper;
	@Autowired
	private TodoService todoService;

	@Test
	void groupsChildrenAndUsesSavedOrderThenIdForEqualDeadlines() throws Exception {
		Todo noDateParent = insert("DueGroup no date parent", 1, null, null);
		Todo noDateChild = insert("DueGroup no date child", 2, null, noDateParent.getId());
		Todo laterGroup = insert("DueGroup later", 3, BASE_DATE.plusDays(2), null);
		Todo tiedGroupLowId = insert("DueGroup tied first", 30, BASE_DATE, null);
		Todo tiedGroupHighId = insert("DueGroup tied second", 30, BASE_DATE, null);
		Todo parent = insert("DueGroup parent", 40, BASE_DATE.plusDays(9), null);
		Todo childWithoutDate = insert("DueGroup child without date", 4, null, parent.getId());
		Todo childLater = insert("DueGroup child later", 5, BASE_DATE.plusDays(4), parent.getId());
		Todo childFirst = insert("DueGroup child first", 6, BASE_DATE, parent.getId());
		Todo childSameDate = insert("DueGroup child same date", 6, BASE_DATE, parent.getId());

		MvcResult due = mockMvc.perform(get("/todo").param("keyword", "DueGroup").param("sort", "due"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("sort", "due"))
				.andReturn();
		assertEquals(ids(tiedGroupLowId, tiedGroupHighId, parent, childFirst, childSameDate, childLater,
				childWithoutDate, laterGroup, noDateParent, noDateChild), shownIds(due));

		MvcResult normal = mockMvc.perform(get("/todo").param("keyword", "DueGroup").param("sort", "normal"))
				.andExpect(status().isOk()).andReturn();
		assertEquals(ids(noDateParent, noDateChild, laterGroup, tiedGroupLowId, tiedGroupHighId, parent,
				childWithoutDate, childLater, childFirst, childSameDate), shownIds(normal));
		mockMvc.perform(get("/todo").param("sort", "due"))
				.andExpect(model().attribute("reorderEnabled", false))
				.andExpect(content().string(containsString("data-reorder-enabled=\"false\"")));
		mockMvc.perform(get("/todo"))
				.andExpect(model().attribute("reorderEnabled", true));

		String csv = mockMvc.perform(get("/todo/export.csv").param("keyword", "DueGroup").param("sort", "due"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertTrue(csv.indexOf("DueGroup no date parent") >= 0);
		assertTrue(csv.indexOf("DueGroup no date parent") < csv.indexOf("DueGroup tied first"));
	}

	@Test
	void ranksGroupsUsingOnlyRowsMatchingAllFilters() throws Exception {
		Long category = todoService.findAllCategories().get(0).getId();
		Long assignee = todoService.findAllAssignees().get(0).getId();
		Todo parent = insert("FilteredDue parent", 1, BASE_DATE.plusDays(8), null);
		Todo visibleChild = insert("FilteredDue child", 2, BASE_DATE.plusDays(4), parent.getId());
		Todo otherGroup = insert("FilteredDue independent", 3, BASE_DATE.plusDays(2), null);
		for (Todo task : List.of(parent, visibleChild, otherGroup)) {
			assign(task, TodoStatus.IN_PROGRESS, category, assignee);
		}
		Todo wrongStatus = insert("FilteredDue completed", 4, BASE_DATE.minusDays(1), parent.getId());
		assign(wrongStatus, TodoStatus.COMPLETED, category, assignee);
		Todo wrongCategory = insert("FilteredDue no category", 5, BASE_DATE.minusDays(2), parent.getId());
		assign(wrongCategory, TodoStatus.IN_PROGRESS, null, assignee);
		Todo wrongAssignee = insert("FilteredDue no assignee", 6, BASE_DATE.minusDays(3), parent.getId());
		assign(wrongAssignee, TodoStatus.IN_PROGRESS, category, null);
		Todo wrongKeyword = insert("Hidden early child", 7, BASE_DATE.minusDays(4), parent.getId());
		assign(wrongKeyword, TodoStatus.IN_PROGRESS, category, assignee);

		MvcResult filtered = mockMvc.perform(get("/todo").param("sort", "due").param("keyword", "FilteredDue")
				.param("status", "IN_PROGRESS").param("categoryId", category.toString())
				.param("assigneeId", assignee.toString()))
				.andExpect(status().isOk()).andExpect(model().attribute("totalCount", 3L)).andReturn();
		assertEquals(ids(otherGroup, parent, visibleChild), shownIds(filtered));
	}

	@Test
	void paginationAndBulkOperationsUseTheSameDueDateOrder() throws Exception {
		List<Todo> tasks = new ArrayList<>();
		for (int index = 0; index < 12; index++) {
			tasks.add(insert("DuePage task " + index, index, BASE_DATE.plusDays(12 - index), null));
		}
		Todo last = tasks.get(0);
		Todo secondLast = tasks.get(1);
		Todo first = tasks.get(11);
		MvcResult firstPage = mockMvc.perform(get("/todo").param("sort", "due").param("keyword", "DuePage"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("currentPage", 1))
				.andExpect(content().string(containsString("page=2&amp;keyword=DuePage")))
				.andExpect(content().string(containsString("sort=due")))
				.andReturn();
		assertEquals(first.getId(), shownIds(firstPage).get(0));
		assertEquals(10, shownIds(firstPage).size());
		MvcResult secondPage = mockMvc.perform(get("/todo").param("sort", "due").param("keyword", "DuePage").param("page", "2"))
				.andExpect(status().isOk()).andExpect(model().attribute("currentPage", 2)).andReturn();
		assertEquals(ids(secondLast, last), shownIds(secondPage));

		mockMvc.perform(post("/todo/bulk/status").with(csrf()).param("sort", "due").param("keyword", "DuePage")
				.param("page", "2").param("ids", last.getId().toString()).param("newStatus", "COMPLETED"))
				.andExpect(flash().attribute("successMessage", containsString("1件")))
				.andExpect(header().string("Location", containsString("sort=due")));
		assertEquals(TodoStatus.COMPLETED, todoMapper.findById(last.getId()).getStatus());

		MockHttpSession session = new MockHttpSession();
		MvcResult preview = mockMvc.perform(post("/todo/bulk/delete/preview").session(session).with(csrf())
				.param("sort", "due").param("keyword", "DuePage").param("page", "2").param("ids", secondLast.getId().toString()))
				.andExpect(view().name("todo/bulk-delete"))
				.andExpect(content().string(containsString("sort=due")))
				.andReturn();
		String confirmationId = (String) preview.getModelAndView().getModel().get("confirmationId");
		mockMvc.perform(post("/todo/bulk/delete/confirm").session(session).with(csrf()).param("confirmationId", confirmationId))
				.andExpect(flash().attribute("successMessage", containsString("1件")))
				.andExpect(header().string("Location", containsString("sort=due")));
		assertNull(todoMapper.findById(secondLast.getId()));

		mockMvc.perform(post("/todo/bulk/status").with(csrf()).param("sort", "due").param("keyword", "DuePage")
				.param("page", "2").param("ids", first.getId().toString()).param("newStatus", "COMPLETED"))
				.andExpect(flash().attribute("errorMessage", containsString("現在のページにないタスク")));
		assertEquals(TodoStatus.NOT_STARTED, todoMapper.findById(first.getId()).getStatus());
	}

	private Todo insert(String title, int order, LocalDate dueDate, Long parentId) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setDisplayOrder(order);
		todo.setDueDate(dueDate);
		todo.setParentId(parentId);
		todoMapper.insert(todo);
		return todo;
	}

	private void assign(Todo task, TodoStatus status, Long categoryId, Long assigneeId) {
		task.setStatus(status);
		task.setCategoryId(categoryId);
		task.setAssigneeId(assigneeId);
		todoMapper.update(task);
	}

	private static List<Long> ids(Todo... tasks) {
		return List.of(tasks).stream().map(Todo::getId).toList();
	}

	private static List<Long> shownIds(MvcResult result) {
		List<?> tasks = (List<?>) result.getModelAndView().getModel().get("todos");
		return tasks.stream().map(Todo.class::cast).map(Todo::getId).toList();
	}
}
