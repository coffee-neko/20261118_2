package com.example.demo;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/todo")
public class TodoController {

	@GetMapping
	public String list(Model model) {
		model.addAttribute("todos", List.of(
				new TodoItem(1, "Spring Boot を学習する", false),
				new TodoItem(2, "ToDo アプリを作成する", true)));
		return "todo/list";
	}

	@GetMapping("/new")
	public String newTodo() {
		return "todo/new";
	}

	@GetMapping("/confirm")
	public String confirm() {
		return "todo/confirm";
	}

	public record TodoItem(int id, String title, boolean completed) {
	}
}
