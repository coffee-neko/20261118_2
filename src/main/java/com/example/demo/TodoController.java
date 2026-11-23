package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.example.demo.model.Todo;
import com.example.demo.model.TodoProgress;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import com.example.demo.service.TodoService.ListPage;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.example.demo.service.TodoService.LIST_PAGE_SIZE;
import static com.example.demo.service.TodoService.parseDate;

@Controller
@RequestMapping("/todo")
public class TodoController {

	static final String SORT_DUE_DATE = "due";
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");
	private static final String STATUS_ALL = "all";

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping
	@Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
	public String list(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = STATUS_ALL) String status,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(defaultValue = "normal") String sort, Model model) {
		TodoStatus selectedStatus = findStatus(status);
		boolean dueDateOrder = SORT_DUE_DATE.equals(sort);
		boolean hasSearchCondition = !keyword.isEmpty() || selectedStatus != null || categoryId != null || assigneeId != null;
		long totalCount = todoService.countByCondition(keyword, selectedStatus, categoryId, assigneeId);
		TodoProgress progress = todoService.findQuestProgress();
		int totalPages = (int) Math.ceil((double) totalCount / LIST_PAGE_SIZE);
		int currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);

		long offset = (currentPage - 1L) * LIST_PAGE_SIZE;
		long startItem = totalCount == 0 ? 0 : offset + 1L;
		long endItem = Math.min(offset + LIST_PAGE_SIZE, totalCount);

		List<Todo> todos = todoService.findPage(new ListPage(currentPage, keyword, selectedStatus, categoryId, assigneeId,
				dueDateOrder));
		model.addAttribute("todos", todos);
		if (!todos.isEmpty()) {
			model.addAttribute("displayNumbers", buildDisplayNumbers(todoService.findAll()));
		}
		model.addAttribute("parentsWithVisibleChildren", todos.stream()
				.map(Todo::getParentId).filter(Objects::nonNull).collect(Collectors.toSet()));
		model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));
		model.addAttribute("keyword", keyword);
		model.addAttribute("status", selectedStatus == null ? STATUS_ALL : selectedStatus.name());
		model.addAttribute("statusOptions", TodoStatus.values());
		model.addAttribute("categories", todoService.findAllCategories());
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("assignees", todoService.findAllAssignees());
		model.addAttribute("assigneeId", assigneeId);
		model.addAttribute("hasSearchCondition", hasSearchCondition);
		model.addAttribute("sort", dueDateOrder ? SORT_DUE_DATE : "normal");
		model.addAttribute("reorderEnabled", !hasSearchCondition && !dueDateOrder);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("allTodoCount", progress.total());
		model.addAttribute("completedTodoCount", progress.completed());
		model.addAttribute("completionRate", progress.percentage());
		model.addAttribute("startItem", startItem);
		model.addAttribute("endItem", endItem);
		return "todo/list";
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("dashboard", todoService.loadDashboard(businessDate(Instant.now())));
		model.addAttribute("statusOptions", TodoStatus.values());
		return "todo/dashboard";
	}

	static LocalDate businessDate(Instant instant) {
		return LocalDate.ofInstant(instant, BUSINESS_ZONE);
	}

	@GetMapping("/export.csv")
	public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = STATUS_ALL) String status,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assigneeId) {
		List<Todo> todos = todoService.findForExport(keyword, findStatus(status), categoryId, assigneeId);
		String date = LocalDate.now(BUSINESS_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
		String filename = "nencho-tasks-" + date + ".csv";
		ContentDisposition attachment = ContentDisposition.attachment().filename(filename).build();
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, attachment.toString())
				.body(TodoCsv.encode(todos));
	}

	@GetMapping("/kanban")
	public String kanban(Model model) {
		TodoStatus[] statuses = TodoStatus.values();
		Map<TodoStatus, List<Todo>> todosByStatus = new EnumMap<>(TodoStatus.class);
		for (TodoStatus status : statuses) {
			todosByStatus.put(status, new ArrayList<>());
		}
		for (Todo todo : todoService.findForKanban()) {
			todosByStatus.get(todo.getStatus()).add(todo);
		}
		model.addAttribute("statusOptions", statuses);
		model.addAttribute("todosByStatus", todosByStatus);
		model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));
		return "todo/kanban";
	}

	@GetMapping("/gantt")
	public String gantt(@RequestParam(name = "month", required = false) String requestedMonth, Model model) {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		YearMonth currentMonth = YearMonth.from(today);
		YearMonth month = currentMonth;
		if (requestedMonth != null) {
			try {
				month = TodoGantt.parseMonth(requestedMonth);
			} catch (IllegalArgumentException | DateTimeParseException exception) {
				model.addAttribute("monthError", "年月は0001-01から9999-12までの範囲で指定してください。今月を表示しています。");
			}
		}

		YearMonth displayMonth = month;
		List<Todo> todos = todoService.findForKanban();
		Map<Long, TodoGantt.Placement> placements = todos.stream().collect(Collectors.toMap(
				Todo::getId, todo -> TodoGantt.place(todo, displayMonth)));
		model.addAttribute("todos", todos);
		model.addAttribute("displayNumbers", buildDisplayNumbers(todos));
		model.addAttribute("placements", placements);
		model.addAttribute("today", today);
		model.addAttribute("month", displayMonth);
		model.addAttribute("previousMonth", TodoGantt.FIRST_MONTH.equals(displayMonth) ? null : displayMonth.minusMonths(1));
		model.addAttribute("nextMonth", TodoGantt.LAST_MONTH.equals(displayMonth) ? null : displayMonth.plusMonths(1));
		model.addAttribute("days", IntStream.rangeClosed(1, displayMonth.lengthOfMonth())
				.mapToObj(displayMonth::atDay).toList());
		return "todo/gantt";
	}

	private Map<Long, String> buildDisplayNumbers(List<Todo> orderedTodos) {
		Map<Long, String> numbers = new HashMap<>();
		Map<Long, Integer> childNumbers = new HashMap<>();
		int rootNumber = 0;
		// The full hierarchy is ordered with each parent before its children.
		for (Todo todo : orderedTodos) {
			Long parentId = todo.getParentId();
			if (parentId == null) {
				numbers.put(todo.getId(), Integer.toString(++rootNumber));
			} else {
				int childNumber = childNumbers.merge(parentId, 1, Integer::sum);
				numbers.put(todo.getId(), numbers.get(parentId) + "-" + childNumber);
			}
		}
		return numbers;
	}

	@GetMapping("/new")
	public String newTodo(Model model) {
		addFormOptions(model, null);
		return "todo/form";
	}

	private void addFormOptions(Model model, Long taskId) {
		model.addAttribute("statusOptions", TodoStatus.values());
		model.addAttribute("categories", todoService.findAllCategories());
		model.addAttribute("assignees", todoService.findAllAssignees());
		model.addAttribute("parentOptions", todoService.findParentOptions(taskId));
	}

	@GetMapping("/{id:\\d+}")
	public String detail(@PathVariable Long id, Model model) {
		Todo todo = todoService.findById(id);
		if (todo == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "指定したタスクが見つかりません。");
		}
		model.addAttribute("todo", todo);
		return "todo/detail";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable Long id, Model model) {
		Todo todo = todoService.findById(id);
		if (todo == null) {
			return "redirect:/todo";
		}
		model.addAttribute("todo", todo);
		addFormOptions(model, id);
		return "todo/edit";
	}

	@GetMapping("/confirm")
	public String confirm() {
		return "todo/confirm";
	}

	@PostMapping("/confirm")
	public String confirm(
			@RequestParam String title,
			@RequestParam String description,
			@RequestParam(defaultValue = "NOT_STARTED") String status,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) String parentId,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String dueDate,
			Model model) {
		addRegistrationInput(model, title, description, status, categoryId, assigneeId, parentId, startDate, dueDate);
		try {
			TodoStatus confirmedStatus = requireStatus(status);
			Todo todo = new Todo();
			todo.setStartDate(parseDate(startDate, "開始日"));
			todo.setDueDate(parseDate(dueDate, "期限"));
			todoService.validateDates(todo);
			model.addAttribute("parent", todoService.findEligibleParent(parseParentId(parentId)));
			model.addAttribute("confirmedStatus", confirmedStatus);
		} catch (IllegalArgumentException exception) {
			model.addAttribute("errorMessage", exception.getMessage());
			return newTodo(model);
		}
		model.addAttribute("category", todoService.findCategoryById(categoryId));
		model.addAttribute("assignee", todoService.findAssigneeById(assigneeId));
		return "todo/confirm";
	}

	@GetMapping("/complete")
	public String complete() {
		return "todo/complete";
	}

	@PostMapping("/complete")
	public String complete(@RequestParam String title, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) String parentId,
			@RequestParam(required = false) String description, @RequestParam(defaultValue = "NOT_STARTED") String status,
			@RequestParam(required = false) String startDate, @RequestParam(required = false) String dueDate,
			Model model) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setCategoryId(categoryId);
		todo.setAssigneeId(assigneeId);
		try {
			todo.setStatus(requireStatus(status));
			todo.setParentId(parseParentId(parentId));
			todo.setStartDate(parseDate(startDate, "開始日"));
			todo.setDueDate(parseDate(dueDate, "期限"));
			todoService.create(todo);
		} catch (IllegalArgumentException exception) {
			addRegistrationInput(model, title, description, status, categoryId, assigneeId, parentId, startDate, dueDate);
			model.addAttribute("errorMessage", exception.getMessage());
			return newTodo(model);
		}
		return "redirect:/todo";
	}

	@PostMapping("/{id}/update")
	public String update(@PathVariable Long id, @RequestParam String title,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) String parentId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String startDate, @RequestParam(required = false) String dueDate,
			Model model, RedirectAttributes redirectAttributes) {
		Todo todo = todoService.findById(id);
		if (todo != null) {
			todo.setTitle(title);
			todo.setCategoryId(categoryId);
			todo.setAssigneeId(assigneeId);
			try {
				if (parentId != null) {
					todo.setParentId(parseParentId(parentId));
				}
				if (status != null) {
					todo.setStatus(requireStatus(status));
				}
				if (startDate != null) {
					todo.setStartDate(parseDate(startDate, "開始日"));
				}
				if (dueDate != null) {
					todo.setDueDate(parseDate(dueDate, "期限"));
				}
				if (todoService.update(todo) > 0) {
					redirectAttributes.addFlashAttribute("successMessage", "更新が完了しました");
					return "redirect:/todo";
				}
			} catch (IllegalArgumentException exception) {
				model.addAttribute("todo", todo);
				model.addAttribute("startDate", startDate);
				model.addAttribute("dueDate", dueDate);
				model.addAttribute("errorMessage", exception.getMessage());
				addFormOptions(model, id);
				return "todo/edit";
			}
		}

		redirectAttributes.addFlashAttribute("errorMessage", "更新に失敗しました");
		return "redirect:/todo";
	}

	private void addRegistrationInput(Model model, String title, String description, String status,
			Long categoryId, Long assigneeId, String parentId, String startDate, String dueDate) {
		model.addAttribute("title", title);
		model.addAttribute("description", description);
		model.addAttribute("status", status);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("assigneeId", assigneeId);
		model.addAttribute("parentId", parentId);
		model.addAttribute("startDate", startDate);
		model.addAttribute("dueDate", dueDate);
	}

	private Long parseParentId(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			return Long.valueOf(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("親タスクの指定が正しくありません。", exception);
		}
	}

	static TodoStatus findStatus(String value) {
		for (TodoStatus status : TodoStatus.values()) {
			if (status.name().equals(value)) {
				return status;
			}
		}
		return null;
	}

	static TodoStatus requireStatus(String value) {
		TodoStatus status = findStatus(value);
		if (status == null) {
			throw new IllegalArgumentException("状態を未着手・進行中・完了から選択してください。");
		}
		return status;
	}

	@PostMapping("/{id}/status")
	public String updateStatus(@PathVariable Long id, @RequestParam String status,
			RedirectAttributes redirectAttributes) {
		try {
			if (!todoService.updateStatus(id, requireStatus(status))) {
				redirectAttributes.addFlashAttribute("errorMessage", "状態の更新に失敗しました");
			}
		} catch (IllegalArgumentException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		}
		return "redirect:/todo";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
		try {
			if (todoService.deleteById(id) > 0) {
				redirectAttributes.addFlashAttribute("successMessage", "ToDoを削除しました");
			} else {
				redirectAttributes.addFlashAttribute("errorMessage", "削除に失敗しました");
			}
		} catch (IllegalArgumentException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		}
		return "redirect:/todo";
	}
}
