package com.example.demo;

import java.time.LocalDate;
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
				.andExpect(content().string(containsString("2026-11-15 の期限タスク")))
				.andExpect(content().string(containsString("href=\"/todo/" + parent.getId() + "\"")))
				.andExpect(content().string(containsString("href=\"/todo/" + child.getId() + "\"")));
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
