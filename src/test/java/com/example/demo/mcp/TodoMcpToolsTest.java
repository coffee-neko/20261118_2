package com.example.demo.mcp;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.JapaneseHolidayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:todoMcpToolsTest",
		"app.mcp.access-token=test-only-token"
})
@ActiveProfiles({ "test", "mcp-local" })
@Transactional
@Import(TodoMcpToolsTest.McpToolTestConfiguration.class)
class TodoMcpToolsTest {

	@Autowired
	private TodoMcpTools tools;

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void listsAndGetsParentAndChildTasks() {
		Todo parent = insert("MCP parent", TodoStatus.IN_PROGRESS, LocalDate.of(2026, 11, 2), null);
		Todo child = insert("MCP child", TodoStatus.NOT_STARTED, LocalDate.of(2026, 11, 5), parent.getId());

		assertTrue(tools.listTodos().stream().anyMatch(todo -> todo.id().equals(parent.getId())));
		assertEquals(parent.getId(), tools.getTodo(parent.getId()).id());
		assertEquals(parent.getId(), tools.getTodo(child.getId()).parentId());
		assertThrows(IllegalArgumentException.class, () -> tools.getTodo(Long.MAX_VALUE));
	}

	@Test
	void findsOnlyBusinessDaysWithoutDueTasksAndExcludesUnknownHolidayDates() {
		insert("Due task", TodoStatus.NOT_STARTED, LocalDate.of(2026, 11, 4), null);

		TodoMcpTools.FreeDaysResult result = tools.findFreeDays("2026-11-02", "2026-11-08");
		assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)),
				result.freeDays());
		assertTrue(tools.findFreeDays("2027-01-04", "2027-01-08").freeDays().isEmpty());
		assertEquals(5, tools.findFreeDays("2027-01-04", "2027-01-08").holidayInformationUnavailableDates().size());
	}

	@Test
	void summarizesTheMondayToSundayWeekIncludingParentAndChildTasks() {
		Todo parent = insert("Weekly parent", TodoStatus.IN_PROGRESS, LocalDate.of(2026, 11, 2), null);
		insert("Weekly child", TodoStatus.COMPLETED, LocalDate.of(2026, 11, 5), parent.getId());
		insert("Outside week", TodoStatus.NOT_STARTED, LocalDate.of(2026, 11, 9), null);

		TodoMcpTools.WeekSummary summary = tools.summarizeWeek("2026-11-04");
		assertEquals(LocalDate.of(2026, 11, 2), summary.weekStart());
		assertEquals(LocalDate.of(2026, 11, 8), summary.weekEnd());
		assertEquals(2, summary.total());
		assertEquals(1L, summary.statusCounts().get(TodoStatus.IN_PROGRESS));
		assertEquals(1L, summary.statusCounts().get(TodoStatus.COMPLETED));
	}

	private Todo insert(String title, TodoStatus status, LocalDate dueDate, Long parentId) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setStatus(status);
		todo.setDueDate(dueDate);
		todo.setParentId(parentId);
		todo.setDisplayOrder(todoMapper.findMaxDisplayOrder() == null ? 1 : todoMapper.findMaxDisplayOrder() + 1);
		todoMapper.insert(todo);
		return todo;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class McpToolTestConfiguration {
		@Bean
		@Primary
		JapaneseHolidayService localHolidayService() {
			return new JapaneseHolidayService(new ObjectMapper()) {
				@Override
				public HolidayPeriod findHolidays(LocalDate startInclusive, LocalDate endInclusive) {
					if (startInclusive.getYear() == 2027) {
						return new HolidayPeriod(Map.of(), Set.of(2027));
					}
					return new HolidayPeriod(Map.of(LocalDate.of(2026, 11, 3), "文化の日"), Set.of());
				}
			};
		}
	}
}
