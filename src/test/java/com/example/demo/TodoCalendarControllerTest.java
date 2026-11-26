package com.example.demo;

import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoCalendarControllerTest")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TodoCalendarControllerTest.CalendarTestConfiguration.class)
class TodoCalendarControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void showsTasksHolidaysAndTheSelectedDaysFullTaskList() throws Exception {
		Todo parent = insert("Calendar parent task", TodoStatus.IN_PROGRESS, LocalDate.of(2026, 11, 15), null);
		Todo child = insert("Calendar child task", TodoStatus.NOT_STARTED, LocalDate.of(2026, 11, 15), parent.getId());
		insert("Calendar completed task", TodoStatus.COMPLETED, LocalDate.of(2026, 11, 15), null);
		insert("Calendar fourth task", TodoStatus.NOT_STARTED, LocalDate.of(2026, 11, 15), null);

		mockMvc.perform(get("/todo/calendar").with(user("calendar-tester").roles("USER"))
				.param("month", "2026-11").param("date", "2026-11-15"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/calendar"))
				.andExpect(content().string(containsString("文化の日")))
				.andExpect(content().string(containsString("Calendar parent task")))
				.andExpect(content().string(containsString("Calendar child task")))
				.andExpect(content().string(containsString("ほか1件")))
				.andExpect(content().string(containsString("2026-11-15 の期限タスク（4件）")))
				.andExpect(content().string(containsString("id=\"selected-date-tasks\"")))
				.andExpect(content().string(containsString("カレンダー上部へ戻る")))
				.andExpect(content().string(containsString("href=\"/todo/" + parent.getId()
						+ "?returnMonth=2026-11&amp;returnDate=2026-11-15\"")))
				.andExpect(content().string(containsString("href=\"/todo/" + child.getId()
						+ "?returnMonth=2026-11&amp;returnDate=2026-11-15\"")));
	}

	@Test
	void acceptsInvalidMonthAndDateWithoutReturningAnErrorPage() throws Exception {
		mockMvc.perform(get("/todo/calendar").with(user("calendar-tester").roles("USER"))
				.param("month", "not-a-month").param("date", "not-a-date"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/calendar"))
				.andExpect(content().string(containsString("日付の指定を読み取れなかったため")));
	}

	@Test
	void providesCalendarLinksFromTheListAndDashboard() throws Exception {
		mockMvc.perform(get("/todo").with(user("calendar-tester").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/todo/calendar\"")));
		mockMvc.perform(get("/todo/dashboard").with(user("calendar-tester").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/todo/calendar\"")));
	}

	@Test
	void showsThisAndNextMonthsInTheDashboardMiniCalendarAndLinksDueDates() throws Exception {
		LocalDate today = TodoController.businessDate(Instant.now());
		YearMonth thisMonth = YearMonth.from(today);
		YearMonth nextMonth = thisMonth.plusMonths(1);
		Todo dueToday = insert("Mini calendar due task", TodoStatus.NOT_STARTED, today, null);

		mockMvc.perform(get("/todo/dashboard").with(user("calendar-tester").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("暦の見張り台")))
				.andExpect(content().string(containsString(thisMonth.getYear() + "年" + thisMonth.getMonthValue() + "月")))
				.andExpect(content().string(containsString(nextMonth.getYear() + "年" + nextMonth.getMonthValue() + "月")))
				.andExpect(content().string(containsString("mini-calendar-task-dot")))
				.andExpect(content().string(containsString("href=\"/todo/calendar?month=" + thisMonth
						+ "&amp;date=" + today + "\"")));

		mockMvc.perform(get("/todo/" + dueToday.getId()).with(user("calendar-tester").roles("USER"))
				.param("returnMonth", thisMonth.toString()).param("returnDate", today.toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("カレンダーに戻る")))
				.andExpect(content().string(containsString("/todo/calendar?month=" + thisMonth + "&amp;date="
						+ today + "#selected-date-tasks")));
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
	static class CalendarTestConfiguration {
		@Bean
		@Primary
		JapaneseHolidayService calendarHolidayService() {
			return new JapaneseHolidayService(new ObjectMapper()) {
				@Override
				public HolidayPeriod findHolidays(LocalDate startInclusive, LocalDate endInclusive) {
					return new HolidayPeriod(Map.of(LocalDate.of(2026, 11, 3), "文化の日"), Set.of());
				}
			};
		}
	}
}
