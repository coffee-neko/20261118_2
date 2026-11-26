package com.example.demo;

import java.net.URI;
import java.util.List;

import com.example.demo.model.Todo;
import com.example.demo.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for integrations. Existing browser pages continue to use TodoController.
 */
@RestController
@RequestMapping("/api/todos")
public class TodoApiController {

	private final TodoService todoService;

	public TodoApiController(TodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping
	public List<Todo> findAll() {
		return todoService.findAll();
	}

	@GetMapping("/{id}")
	public Todo findById(@PathVariable Long id) {
		return requireTodo(id);
	}

	@PostMapping
	public ResponseEntity<Todo> create(@RequestBody TodoRequest request) {
		Todo todo = applyRequest(request, new Todo());
		try {
			todoService.create(todo);
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
		return ResponseEntity.created(URI.create("/api/todos/" + todo.getId())).body(todoService.findById(todo.getId()));
	}

	@PutMapping("/{id}")
	public Todo update(@PathVariable Long id, @RequestBody TodoRequest request) {
		Todo todo = requireTodo(id);
		applyRequest(request, todo);
		try {
			if (todoService.update(todo) == 0) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
			}
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
		return todoService.findById(id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		try {
			if (todoService.deleteById(id) == 0) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
			}
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
		return ResponseEntity.noContent().build();
	}

	private Todo requireTodo(Long id) {
		Todo todo = todoService.findById(id);
		if (todo == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
		}
		return todo;
	}

	private Todo applyRequest(TodoRequest request, Todo todo) {
		if (request == null || request.title() == null || request.title().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required.");
		}
		try {
			todo.setTitle(request.title());
			todo.setStatus(TodoController.requireStatus(request.status()));
			todo.setCategoryId(request.categoryId());
			todo.setAssigneeId(request.assigneeId());
			todo.setParentId(request.parentId());
			todo.setStartDate(TodoService.parseDate(request.startDate(), "Start date"));
			todo.setDueDate(TodoService.parseDate(request.dueDate(), "Due date"));
			return todo;
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
	}

	private ResponseStatusException badRequest(IllegalArgumentException exception) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
	}

	/**
	 * Full task representation for POST and PUT. Optional values are represented by JSON null.
	 */
	public record TodoRequest(String title, String status, Long categoryId, Long assigneeId, Long parentId,
			String startDate, String dueDate) {
	}
}
