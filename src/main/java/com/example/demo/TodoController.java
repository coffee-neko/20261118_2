package com.example.demo;

import com.example.demo.model.Todo;
import com.example.demo.service.TodoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/todo")
public class TodoController {

	private static final int PAGE_SIZE = 10;
	private static final String STATUS_ALL = "all";
	private static final String STATUS_INCOMPLETE = "incomplete";
	private static final String STATUS_COMPLETED = "completed";

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = STATUS_ALL) String status,
			@RequestParam(required = false) Long categoryId, Model model) {
		String selectedStatus = normalizeStatus(status);
		Boolean completed = toCompleted(selectedStatus);
		long totalCount = todoService.countByCondition(keyword, completed, categoryId);
		int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
		int currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);

		int offset = (currentPage - 1) * PAGE_SIZE;
		long startItem = totalCount == 0 ? 0 : offset + 1L;
		long endItem = Math.min(offset + PAGE_SIZE, totalCount);

		model.addAttribute("todos", todoService.findPage(keyword, completed, categoryId, PAGE_SIZE, offset));
		model.addAttribute("keyword", keyword);
		model.addAttribute("status", selectedStatus);
		model.addAttribute("categories", todoService.findAllCategories());
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("hasSearchCondition",
				!keyword.isEmpty() || !STATUS_ALL.equals(selectedStatus) || categoryId != null);
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("startItem", startItem);
		model.addAttribute("endItem", endItem);
		return "todo/list";
	}

	private String normalizeStatus(String status) {
		if (STATUS_INCOMPLETE.equals(status) || STATUS_COMPLETED.equals(status)) {
			return status;
		}
		return STATUS_ALL;
	}

	private Boolean toCompleted(String status) {
		if (STATUS_INCOMPLETE.equals(status)) {
			return false;
		}
		if (STATUS_COMPLETED.equals(status)) {
			return true;
		}
		return null;
	}

	@GetMapping("/new")
	public String newTodo(Model model) {
		model.addAttribute("categories", todoService.findAllCategories());
		return "todo/form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable Long id, Model model) {
		Todo todo = todoService.findById(id);
		if (todo == null) {
			return "redirect:/todo";
		}
		model.addAttribute("todo", todo);
		model.addAttribute("categories", todoService.findAllCategories());
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
			@RequestParam(required = false) Boolean done,
			@RequestParam(required = false) Long categoryId,
			Model model) {
		model.addAttribute("title", title);
		model.addAttribute("description", description);
		model.addAttribute("done", Boolean.TRUE.equals(done));
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("category", todoService.findCategoryById(categoryId));
		return "todo/confirm";
	}

	@GetMapping("/complete")
	public String complete() {
		return "todo/complete";
	}

	@PostMapping("/complete")
	public String complete(@RequestParam String title, @RequestParam(required = false) Long categoryId,
			RedirectAttributes redirectAttributes) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setCompleted(false);
		todo.setCategoryId(categoryId);
		try {
			todoService.create(todo);
		} catch (IllegalArgumentException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
			return "redirect:/todo/new";
		}
		return "redirect:/todo";
	}

	@PostMapping("/{id}/update")
	public String update(@PathVariable Long id, @RequestParam String title,
			@RequestParam(required = false) Long categoryId,
			RedirectAttributes redirectAttributes) {
		Todo todo = todoService.findById(id);
		if (todo != null) {
			if (categoryId != null && todoService.findCategoryById(categoryId) == null) {
				redirectAttributes.addFlashAttribute("errorMessage", "指定されたカテゴリは存在しません。");
				return "redirect:/todo/" + id + "/edit";
			}
			todo.setTitle(title);
			todo.setCategoryId(categoryId);
			if (todoService.update(todo) > 0) {
				redirectAttributes.addFlashAttribute("successMessage", "更新が完了しました");
				return "redirect:/todo";
			}
		}

		redirectAttributes.addFlashAttribute("errorMessage", "更新に失敗しました");
		return "redirect:/todo";
	}

	@PostMapping("/{id}/toggle")
	public String toggleCompleted(@PathVariable Long id) {
		todoService.toggleCompleted(id);
		return "redirect:/todo";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
		if (todoService.deleteById(id) > 0) {
			redirectAttributes.addFlashAttribute("successMessage", "ToDoを削除しました");
		} else {
			redirectAttributes.addFlashAttribute("errorMessage", "削除に失敗しました");
		}
		return "redirect:/todo";
	}
}
