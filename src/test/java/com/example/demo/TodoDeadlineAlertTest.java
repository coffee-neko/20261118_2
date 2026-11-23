package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import com.example.demo.model.Todo;
import com.example.demo.model.TodoProgress;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoDeadlineAlertTest")
@AutoConfigureMockMvc
@Import(TodoDeadlineAlertTest.FixedDateConfig.class)
@WithMockUser
class TodoDeadlineAlertTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);
	private static final Pattern ALERT = Pattern.compile(
			"<span\\b[^>]*class=\"[^\"]*\\btodo-deadline-alert\\b[^\"]*\"[^>]*>[^<]+</span>");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TodoService todoService;

	@Test
	void rendersOneAlertPerEligibleRowUsingItsOwnDeadlineAndStatus() throws Exception {
		Todo parent = task(1, TODAY.minusDays(1), TodoStatus.IN_PROGRESS);
		parent.setChildCount(1);
		Todo child = task(2, TODAY, TodoStatus.NOT_STARTED);
		child.setParentId(parent.getId());
		child.setParentTitle(parent.getTitle());
		List<Todo> tasks = List.of(parent, child,
				task(3, TODAY.plusDays(1), TodoStatus.IN_PROGRESS),
				task(4, TODAY.plusDays(3), TodoStatus.NOT_STARTED),
				task(5, TODAY.plusDays(4), TodoStatus.NOT_STARTED),
				task(6, TODAY.minusDays(1), TodoStatus.COMPLETED),
				task(7, TODAY, TodoStatus.COMPLETED),
				task(8, TODAY.plusDays(1), TodoStatus.COMPLETED),
				task(9, null, TodoStatus.NOT_STARTED));
		when(todoService.countByCondition("", null, null, null)).thenReturn((long) tasks.size());
		when(todoService.findQuestProgress()).thenReturn(new TodoProgress(8, 3));
		when(todoService.findPage("", null, null, null, TodoService.LIST_PAGE_SIZE, 0L)).thenReturn(tasks);
		when(todoService.findAll()).thenReturn(tasks);

		String html = mockMvc.perform(get("/todo")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertAlert(html, 1, "期限超過", "text-bg-danger");
		assertAlert(html, 2, "本日締切", "todo-deadline-today");
		assertAlert(html, 3, "期限間近", "text-bg-warning");
		assertAlert(html, 4, "期限間近", "text-bg-warning");
		for (long id = 5; id <= 9; id++) {
			assertEquals(0, alertsInRow(html, id).size(), "タスクID " + id);
		}
	}

	private static Todo task(long id, LocalDate dueDate, TodoStatus status) {
		Todo todo = new Todo();
		todo.setId(id);
		todo.setTitle("期限アラート確認 " + id);
		todo.setDueDate(dueDate);
		todo.setStatus(status);
		return todo;
	}

	private static void assertAlert(String html, long id, String label, String colorClass) {
		List<String> alerts = alertsInRow(html, id);
		assertEquals(1, alerts.size(), "タスクID " + id);
		assertTrue(alerts.get(0).contains(colorClass), "タスクID " + id);
		assertTrue(alerts.get(0).contains(">" + label + "</span>"), "タスクID " + id);
	}

	private static List<String> alertsInRow(String html, long id) {
		var row = Pattern.compile("(?s)<tr\\b[^>]*\\bdata-todo-id=\"" + id + "\"[^>]*>(.*?)</tr>").matcher(html);
		assertTrue(row.find(), "タスクID " + id + " の行");
		return ALERT.matcher(row.group(1)).results().map(match -> match.group()).toList();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedDateConfig implements WebMvcConfigurer {
		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(new HandlerInterceptor() {
				@Override
				public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
						ModelAndView modelAndView) {
					if (modelAndView != null) {
						modelAndView.addObject("today", TODAY);
					}
				}
			}).addPathPatterns("/todo");
		}
	}
}
