package com.example.demo.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.demo.mapper.CategoryMapper;
import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Category;
import com.example.demo.model.Todo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoService {

	private final TodoMapper todoMapper;
	private final CategoryMapper categoryMapper;

	public TodoService(TodoMapper todoMapper, CategoryMapper categoryMapper) {
		this.todoMapper = todoMapper;
		this.categoryMapper = categoryMapper;
	}

	@Transactional
	public void create(Todo todo) {
		validateCategory(todo.getCategoryId());
		Integer maxDisplayOrder = todoMapper.findMaxDisplayOrder();
		todo.setDisplayOrder(maxDisplayOrder == null ? 1 : maxDisplayOrder + 1);
		todoMapper.insert(todo);
	}

	public List<Todo> findAll() {
		return todoMapper.findAll();
	}

	public List<Todo> findPage(String keyword, Boolean completed, Long categoryId, int limit, int offset) {
		return todoMapper.findPage(keyword, completed, categoryId, limit, offset);
	}

	public long countByCondition(String keyword, Boolean completed, Long categoryId) {
		return todoMapper.countByCondition(keyword, completed, categoryId);
	}

	public List<Category> findAllCategories() {
		return categoryMapper.findAll();
	}

	public Category findCategoryById(Long categoryId) {
		return categoryId == null ? null : categoryMapper.findById(categoryId);
	}

	public Todo findById(Long id) {
		return todoMapper.findById(id);
	}

	public int update(Todo todo) {
		validateCategory(todo.getCategoryId());
		return todoMapper.update(todo);
	}

	public boolean toggleCompleted(Long id) {
		Todo todo = todoMapper.findById(id);
		if (todo == null) {
			return false;
		}

		todo.setCompleted(!Boolean.TRUE.equals(todo.getCompleted()));
		return todoMapper.update(todo) > 0;
	}

	public int deleteById(Long id) {
		return todoMapper.deleteById(id);
	}

	private void validateCategory(Long categoryId) {
		if (categoryId != null && categoryMapper.findById(categoryId) == null) {
			throw new IllegalArgumentException("指定されたカテゴリは存在しません。");
		}
	}

	@Transactional
	public void reorderPage(int page, int pageSize, List<Long> todoIds) {
		if (page < 1 || todoIds == null || todoIds.isEmpty() || todoIds.stream().anyMatch(id -> id == null)) {
			throw new IllegalArgumentException("並び順のリクエストが不正です。");
		}

		Set<Long> requestedIds = new HashSet<>(todoIds);
		if (requestedIds.size() != todoIds.size()) {
			throw new IllegalArgumentException("同じタスクIDを複数回指定することはできません。");
		}

		List<Todo> currentPageTodos = todoMapper.findPageForUpdate(pageSize, (page - 1) * pageSize);
		Set<Long> currentPageIds = new HashSet<>();
		for (Todo todo : currentPageTodos) {
			currentPageIds.add(todo.getId());
		}

		if (currentPageTodos.isEmpty() || !currentPageIds.equals(requestedIds)) {
			throw new IllegalArgumentException("表示中のページにあるタスクだけを並び替えてください。");
		}

		for (int index = 0; index < todoIds.size(); index++) {
			int displayOrder = currentPageTodos.get(index).getDisplayOrder();
			if (todoMapper.updateDisplayOrder(todoIds.get(index), displayOrder) != 1) {
				throw new IllegalStateException("並び順の保存に失敗しました。");
			}
		}
	}
}
