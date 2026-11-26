package com.example.demo.mcp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.JapaneseHolidayService;
import com.example.demo.service.JapaneseHolidayService.HolidayPeriod;
import com.example.demo.service.TodoService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Read-only MCP tools available only when the local MCP profile is active. */
@Component
@Profile("mcp-local")
public class TodoMcpTools {

	private static final int MAX_FREE_DAY_RANGE_DAYS = 366;
	private final TodoService todoService;
	private final JapaneseHolidayService holidayService;

	public TodoMcpTools(TodoService todoService, JapaneseHolidayService holidayService) {
		this.todoService = todoService;
		this.holidayService = holidayService;
	}

	@McpTool(name = "list_todos", description = "Lists all tasks, including parent and child tasks.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true))
	public List<TodoView> listTodos() {
		return todoService.findAll().stream().map(TodoView::from).toList();
	}

	@McpTool(name = "get_todo", description = "Gets one task by its numeric ID.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true))
	public TodoView getTodo(@McpToolParam(description = "The task ID.") Long id) {
		if (id == null || id < 1) {
			throw new IllegalArgumentException("Task ID must be a positive number.");
		}
		Todo todo = todoService.findById(id);
		if (todo == null) {
			throw new IllegalArgumentException("The requested task was not found.");
		}
		return TodoView.from(todo);
	}

	@McpTool(name = "find_free_days", description = "Finds Japanese business days without due tasks. Unknown holiday dates are excluded.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true))
	public FreeDaysResult findFreeDays(
			@McpToolParam(description = "Inclusive start date in yyyy-MM-dd format.") String startDate,
			@McpToolParam(description = "Inclusive end date in yyyy-MM-dd format.") String endDate) {
		LocalDate start = parseDate(startDate, "startDate");
		LocalDate end = parseDate(endDate, "endDate");
		if (end.isBefore(start) || end.toEpochDay() - start.toEpochDay() > MAX_FREE_DAY_RANGE_DAYS) {
			throw new IllegalArgumentException("The date range must be between 0 and 366 days.");
		}

		Set<LocalDate> dueDates = new HashSet<>();
		for (Todo todo : todoService.findForKanban()) {
			if (todo.getDueDate() != null) {
				dueDates.add(todo.getDueDate());
			}
		}
		HolidayPeriod holidays = holidayService.findHolidays(start, end);
		List<LocalDate> unavailableHolidayDates = new java.util.ArrayList<>();
		List<LocalDate> freeDays = new java.util.ArrayList<>();
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			if (isWeekend(date)) {
				continue;
			}
			if (holidays.isUnknown(date)) {
				unavailableHolidayDates.add(date);
				continue;
			}
			if (!holidays.holidays().containsKey(date) && !dueDates.contains(date)) {
				freeDays.add(date);
			}
		}
		return new FreeDaysResult(start, end, List.copyOf(freeDays), List.copyOf(unavailableHolidayDates));
	}

	@McpTool(name = "summarize_week", description = "Summarizes tasks due Monday through Sunday for the week containing the supplied date.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true))
	public WeekSummary summarizeWeek(
			@McpToolParam(description = "Any date in the target week, in yyyy-MM-dd format.") String dateInWeek) {
		LocalDate requestedDate = parseDate(dateInWeek, "dateInWeek");
		LocalDate weekStart = requestedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate weekEnd = weekStart.plusDays(6);
		Map<TodoStatus, Long> counts = new EnumMap<>(TodoStatus.class);
		for (TodoStatus status : TodoStatus.values()) {
			counts.put(status, 0L);
		}
		List<TodoView> tasks = todoService.findForKanban().stream()
				.filter(todo -> todo.getDueDate() != null && !todo.getDueDate().isBefore(weekStart)
						&& !todo.getDueDate().isAfter(weekEnd))
				.peek(todo -> counts.compute(todo.getStatus(), (ignored, count) -> count + 1))
				.map(TodoView::from)
				.toList();
		return new WeekSummary(weekStart, weekEnd, tasks.size(), Map.copyOf(counts), tasks);
	}

	private static boolean isWeekend(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

	private static LocalDate parseDate(String value, String parameterName) {
		if (value == null) {
			throw new IllegalArgumentException(parameterName + " is required.");
		}
		try {
			LocalDate date = LocalDate.parse(value);
			if (value.length() != 10 || date.getYear() < 1) {
				throw new IllegalArgumentException(parameterName + " must use yyyy-MM-dd format.");
			}
			return date;
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException(parameterName + " must use yyyy-MM-dd format.", exception);
		}
	}

	public record TodoView(Long id, String title, TodoStatus status, Long parentId, String parentTitle,
			Long categoryId, String categoryName, Long assigneeId, String assigneeName, LocalDate startDate,
			LocalDate dueDate) {
		static TodoView from(Todo todo) {
			return new TodoView(todo.getId(), todo.getTitle(), todo.getStatus(), todo.getParentId(), todo.getParentTitle(),
					todo.getCategoryId(), todo.getCategoryName(), todo.getAssigneeId(), todo.getAssigneeName(),
					todo.getStartDate(), todo.getDueDate());
		}
	}

	public record FreeDaysResult(LocalDate startDate, LocalDate endDate, List<LocalDate> freeDays,
			List<LocalDate> holidayInformationUnavailableDates) {
	}

	public record WeekSummary(LocalDate weekStart, LocalDate weekEnd, int total, Map<TodoStatus, Long> statusCounts,
			List<TodoView> tasks) {
	}
}
