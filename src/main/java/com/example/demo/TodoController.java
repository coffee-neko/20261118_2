package com.example.demo;

import com.example.demo.model.Todo;
import com.example.demo.service.TodoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

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
}
