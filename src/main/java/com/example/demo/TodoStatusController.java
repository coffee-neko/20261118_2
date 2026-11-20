package com.example.demo;

import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/todo")
public class TodoStatusController {

	private final TodoService todoService;

	public TodoStatusController(TodoService todoService) {
		this.todoService = todoService;
	}

	@PutMapping("/{id}/status")
	public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestBody StatusUpdate request) {
		if (request == null || request.status() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "変更先の状態が必要です。");
		}
		TodoStatus status;
		try {
			status = TodoStatus.valueOf(request.status());
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "変更先の状態が正しくありません。", exception);
		}
		if (!todoService.updateStatus(id, status)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つかりません。");
		}
		return ResponseEntity.noContent().build();
	}

	public record StatusUpdate(String status) {
	}
}
