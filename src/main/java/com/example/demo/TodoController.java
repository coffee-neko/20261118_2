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

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("todos", todoService.findAll());
		return "todo/list";
	}

	@GetMapping("/new")
	public String newTodo() {
		return "todo/form";
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
