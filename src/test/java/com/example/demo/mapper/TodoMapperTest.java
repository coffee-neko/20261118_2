package com.example.demo.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TodoMapperTest {

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void insertAndFindAll() {
		Todo todo = new Todo();
		todo.setTitle("MyBatis task");
		todo.setDisplayOrder(100);

		assertEquals(1, todoMapper.insert(todo));
		assertNotNull(todo.getId());
		assertEquals("MyBatis task", todoMapper.findById(todo.getId()).getTitle());
		assertEquals(TodoStatus.NOT_STARTED, todoMapper.findById(todo.getId()).getStatus());
		todo.setTitle("Updated task");
		todo.setStatus(TodoStatus.COMPLETED);
		assertEquals(1, todoMapper.update(todo));
		assertEquals("Updated task", todoMapper.findById(todo.getId()).getTitle());
		assertEquals(TodoStatus.COMPLETED, todoMapper.findById(todo.getId()).getStatus());
		assertEquals(1L, todoMapper.countByCondition("Updated task", TodoStatus.COMPLETED, null, null));
		assertEquals(0L, todoMapper.countByCondition("Updated task", TodoStatus.IN_PROGRESS, null, null));
		assertTrue(todoMapper.findAll().stream()
				.anyMatch(savedTodo -> savedTodo.getId().equals(todo.getId())));
		assertEquals(1, todoMapper.deleteById(todo.getId()));
		assertTrue(todoMapper.findAll().stream()
				.noneMatch(savedTodo -> savedTodo.getId().equals(todo.getId())));
	}
}
