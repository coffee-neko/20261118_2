package com.example.demo.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.example.demo.mapper.AssigneeMapper;
import com.example.demo.mapper.CategoryMapper;
import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Assignee;
import com.example.demo.model.Category;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoProgress;
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
		if (todo.getParentId() != null) {
			if (todoMapper.lockById(todo.getParentId()) == null) {
				throw new IllegalArgumentException("指定した親タスクは存在しません。");
			}
			validateParent(todo);
		}
		Integer maxDisplayOrder = todoMapper.findMaxDisplayOrder();
		todo.setDisplayOrder(maxDisplayOrder == null ? 1 : maxDisplayOrder + 1);
		todoMapper.insert(todo);
	}

	public List<Todo> findAll() {
		return todoMapper.findAll();
	}

	public List<Todo> findForKanban() {
		return todoMapper.findForKanban();
	}

	public List<Todo> findForExport(String keyword, TodoStatus status, Long categoryId, Long assigneeId) {
		return todoMapper.findForExport(keyword, status, categoryId, assigneeId);
	}

	public List<Todo> findPage(String keyword, TodoStatus status, Long categoryId, Long assigneeId, int limit, long offset) {
		return todoMapper.findPage(keyword, status, categoryId, assigneeId, limit, offset);
	}

	public long countByCondition(String keyword, TodoStatus status, Long categoryId, Long assigneeId) {
		return todoMapper.countByCondition(keyword, status, categoryId, assigneeId);
	}

	public TodoProgress findQuestProgress() {
		return todoMapper.findQuestProgress();
	}

	public List<Todo> findParentOptions(Long taskId) {
		return todoMapper.findParentOptions(taskId);
	}

	public Todo findEligibleParent(Long parentId) {
		if (parentId == null) {
			return null;
		}
		Todo parent = todoMapper.findById(parentId);
		if (parent == null) {
			throw new IllegalArgumentException("指定した親タスクは存在しません。");
		}
		if (parent.getParentId() != null) {
			throw new IllegalArgumentException("子タスクを親には指定できません。");
		}
		return parent;
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

	@Transactional
	public int update(Todo todo) {
		validateDates(todo);
		validateCategory(todo.getCategoryId());
		validateAssignee(todo.getAssigneeId());
		SortedSet<Long> taskIds = new TreeSet<>();
		taskIds.add(todo.getId());
		if (todo.getParentId() != null) {
			taskIds.add(todo.getParentId());
		}
		lockTasks(taskIds);
		validateParent(todo);
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

	@Transactional
	public int deleteById(Long id) {
		if (todoMapper.lockById(id) == null) {
			return 0;
		}
		if (todoMapper.hasChildren(id)) {
			throw new IllegalArgumentException("子タスクが残っているため、この親タスクは削除できません。");
		}
		return todoMapper.deleteById(id);
	}

	private void lockTasks(SortedSet<Long> sortedIds) {
		// Acquire overlapping task locks in ID order before checking the relationships.
		for (Long id : sortedIds) {
			if (todoMapper.lockById(id) == null) {
				throw new IllegalArgumentException("対象タスクまたは指定した親タスクが見つかりません。");
			}
		}
	}

	private void validateParent(Todo todo) {
		Long parentId = todo.getParentId();
		if (parentId == null) {
			return;
		}
		if (parentId.equals(todo.getId())) {
			throw new IllegalArgumentException("自分自身を親タスクには指定できません。");
		}
		findEligibleParent(parentId);
		if (todo.getId() != null && todoMapper.hasChildren(todo.getId())) {
			throw new IllegalArgumentException("子タスクを持つタスクは、別のタスクの子に変更できません。");
		}
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
	public void reorderPage(int page, int pageSize, Long parentId, List<Long> todoIds) {
		if (page < 1 || todoIds == null || todoIds.isEmpty() || todoIds.size() > pageSize
				|| todoIds.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("並び順のリクエストが不正です。");
		}

		Set<Long> requestedIds = new HashSet<>(todoIds);
		if (requestedIds.size() != todoIds.size()) {
			throw new IllegalArgumentException("同じタスクIDを複数回指定することはできません。");
		}

		SortedSet<Long> taskIds = new TreeSet<>(requestedIds);
		if (parentId != null) {
			taskIds.add(parentId);
		}
		lockTasks(taskIds);
		List<Todo> siblingTasks = todoMapper.findPage("", null, null, null, pageSize, (page - 1L) * pageSize)
				.stream()
				.filter(todo -> Objects.equals(todo.getParentId(), parentId))
				.toList();
		Set<Long> currentPageIds = new HashSet<>();
		for (Todo todo : siblingTasks) {
			currentPageIds.add(todo.getId());
		}

		if (!currentPageIds.equals(requestedIds)) {
			throw new IllegalArgumentException("表示中のページで、同じ親に属するタスク同士だけを並び替えてください。");
		}

		for (int index = 0; index < todoIds.size(); index++) {
			int displayOrder = siblingTasks.get(index).getDisplayOrder();
			if (todoMapper.updateDisplayOrder(todoIds.get(index), displayOrder) != 1) {
				throw new IllegalStateException("並び順の保存に失敗しました。");
			}
		}
	}
}
