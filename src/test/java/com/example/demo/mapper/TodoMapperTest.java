package com.example.demo.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TodoMapperTest {

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void insertAndFindAll() {
		Todo todo = new Todo();
		todo.setTitle("MyBatis task");
		todo.setCompleted(false);

		assertEquals(1, todoMapper.insert(todo));
		assertNotNull(todo.getId());
		assertEquals("MyBatis task", todoMapper.findById(todo.getId()).getTitle());
		todo.setTitle("Updated task");
		todo.setCompleted(true);
		assertEquals(1, todoMapper.update(todo));
		assertEquals("Updated task", todoMapper.findById(todo.getId()).getTitle());
		assertTrue(todoMapper.findById(todo.getId()).getCompleted());
		assertTrue(todoMapper.findAll().stream()
				.anyMatch(savedTodo -> savedTodo.getId().equals(todo.getId())));
		assertEquals(1, todoMapper.deleteById(todo.getId()));
		assertTrue(todoMapper.findAll().stream()
				.noneMatch(savedTodo -> savedTodo.getId().equals(todo.getId())));
	}
}
