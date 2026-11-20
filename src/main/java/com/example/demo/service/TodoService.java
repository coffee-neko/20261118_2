package com.example.demo.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.demo.mapper.AssigneeMapper;
import com.example.demo.mapper.CategoryMapper;
import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Assignee;
import com.example.demo.model.Category;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoService {

	private final TodoMapper todoMapper;
	private final CategoryMapper categoryMapper;
	private final AssigneeMapper assigneeMapper;

	public TodoService(TodoMapper todoMapper, CategoryMapper categoryMapper, AssigneeMapper assigneeMapper) {
		this.todoMapper = todoMapper;
		this.categoryMapper = categoryMapper;
		this.assigneeMapper = assigneeMapper;
	}

	@Transactional
	public void create(Todo todo) {
		validateDates(todo);
		validateCategory(todo.getCategoryId());
		validateAssignee(todo.getAssigneeId());
		Integer maxDisplayOrder = todoMapper.findMaxDisplayOrder();
		todo.setDisplayOrder(maxDisplayOrder == null ? 1 : maxDisplayOrder + 1);
		todoMapper.insert(todo);
	}

	public List<Todo> findAll() {
		return todoMapper.findAll();
	}

	public List<Todo> findPage(String keyword, TodoStatus status, Long categoryId, Long assigneeId, int limit, int offset) {
		return todoMapper.findPage(keyword, status, categoryId, assigneeId, limit, offset);
	}

	public long countByCondition(String keyword, TodoStatus status, Long categoryId, Long assigneeId) {
		return todoMapper.countByCondition(keyword, status, categoryId, assigneeId);
	}

	public long countAll() {
		return todoMapper.countAll();
	}

	public long countCompleted() {
		return todoMapper.countCompleted();
	}

	public List<Category> findAllCategories() {
		return categoryMapper.findAll();
	}

	public Category findCategoryById(Long categoryId) {
		return categoryId == null ? null : categoryMapper.findById(categoryId);
	}

	public List<Assignee> findAllAssignees() {
		return assigneeMapper.findAll();
	}

	public Assignee findAssigneeById(Long assigneeId) {
		return assigneeId == null ? null : assigneeMapper.findById(assigneeId);
	}

	public Todo findById(Long id) {
		return todoMapper.findById(id);
	}

	public int update(Todo todo) {
		validateDates(todo);
		validateCategory(todo.getCategoryId());
		validateAssignee(todo.getAssigneeId());
		return todoMapper.update(todo);
	}

	public boolean updateStatus(Long id, TodoStatus status) {
		return todoMapper.updateStatus(id, status) > 0;
	}

	public void validateDates(Todo todo) {
		if (todo.getStartDate() != null && todo.getDueDate() != null
				&& todo.getStartDate().isAfter(todo.getDueDate())) {
			throw new IllegalArgumentException("開始日は期限以前の日付を入力してください。");
		}
	}

	public int deleteById(Long id) {
		return todoMapper.deleteById(id);
	}

	private void validateCategory(Long categoryId) {
		if (categoryId != null && categoryMapper.findById(categoryId) == null) {
			throw new IllegalArgumentException("指定されたカテゴリは存在しません。");
		}
	}

	private void validateAssignee(Long assigneeId) {
		if (assigneeId != null && assigneeMapper.findById(assigneeId) == null) {
			throw new IllegalArgumentException("指定された担当者は存在しません。");
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
