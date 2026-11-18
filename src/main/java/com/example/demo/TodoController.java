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

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "1") int page, Model model) {
		long totalCount = todoService.countAll();
		int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
		int currentPage = Math.max(page, 1);

		if (totalPages > 0) {
			currentPage = Math.min(currentPage, totalPages);
		}

		int offset = (currentPage - 1) * PAGE_SIZE;
		long startItem = totalCount == 0 ? 0 : offset + 1L;
		long endItem = Math.min(offset + PAGE_SIZE, totalCount);

		model.addAttribute("todos", todoService.findPage(PAGE_SIZE, offset));
		model.addAttribute("currentPage", currentPage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("startItem", startItem);
		model.addAttribute("endItem", endItem);
		return "todo/list";
	}

	@GetMapping("/new")
	public String newTodo() {
		return "todo/form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable Long id, Model model) {
		Todo todo = todoService.findById(id);
		if (todo == null) {
			return "redirect:/todo";
		}
		model.addAttribute("todo", todo);
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
			Model model) {
		model.addAttribute("title", title);
		model.addAttribute("description", description);
		model.addAttribute("done", Boolean.TRUE.equals(done));
		return "todo/confirm";
	}

	@GetMapping("/complete")
	public String complete() {
		return "todo/complete";
	}

	@PostMapping("/complete")
	public String complete(@RequestParam String title) {
		Todo todo = new Todo();
		todo.setTitle(title);
		todo.setCompleted(false);
		todoService.create(todo);
		return "redirect:/todo";
	}

	@PostMapping("/{id}/update")
	public String update(@PathVariable Long id, @RequestParam String title,
			RedirectAttributes redirectAttributes) {
		Todo todo = todoService.findById(id);
		if (todo != null) {
			todo.setTitle(title);
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
