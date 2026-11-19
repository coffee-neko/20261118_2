package com.example.demo;

import java.util.List;

import com.example.demo.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/todo")
public class TodoReorderController {

	private static final int PAGE_SIZE = 10;

	private final TodoService todoService;

	public TodoReorderController(TodoService todoService) {
		this.todoService = todoService;
	}

	@PutMapping("/reorder")
	public ResponseEntity<Void> reorder(@RequestBody TodoReorderRequest request) {
		if (request == null || request.page() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ページ番号が必要です。");
		}

		try {
			todoService.reorderPage(request.page(), PAGE_SIZE, request.todoIds());
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
		}
	}

	public record TodoReorderRequest(Integer page, List<Long> todoIds) {
	}
}
