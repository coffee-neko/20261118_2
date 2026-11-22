package com.example.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.demo.TodoCsvReader.Row;
import com.example.demo.model.Assignee;
import com.example.demo.model.Category;
import com.example.demo.model.Todo;
import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoCsvImportService {
	record Issue(int line, String reason) {}

	record Review(List<Issue> errors, List<Issue> warnings) {}

	private record PreparedTask(int line, Long id, Long parentId, Integer order, Todo todo) {}

	private record Validation(Review review, List<PreparedTask> tasks) {}

	static final class RegistrationFailure extends RuntimeException {
		private final int line;

		RegistrationFailure(int line, RuntimeException cause) {
			super(cause instanceof IllegalArgumentException
					? cause.getMessage() + " 今回の取り込みを取り消しました。"
					: "タスクの登録に失敗したため、今回の取り込みを取り消しました。", cause);
			this.line = line;
		}

		int line() {
			return line;
		}
	}

	private final TodoService todoService;

	public TodoCsvImportService(TodoService todoService) {
		this.todoService = todoService;
	}

	Review preview(List<Row> rows) {
		return validate(rows).review();
	}

	@Transactional
	public Review register(List<Row> rows) {
		Validation validation = validate(rows);
		if (!validation.review().errors().isEmpty()) {
			return validation.review();
		}
		List<PreparedTask> ordered = validation.tasks().stream()
				.sorted(Comparator.comparing(PreparedTask::order, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		Map<Long, Long> databaseIds = new HashMap<>();
		for (PreparedTask task : ordered) {
			if (task.parentId() == null) {
				create(task);
				if (task.id() != null) {
					databaseIds.put(task.id(), task.todo().getId());
				}
			}
		}
		for (PreparedTask task : ordered) {
			if (task.parentId() != null) {
				task.todo().setParentId(databaseIds.get(task.parentId()));
				create(task);
			}
		}
		return validation.review();
	}

	private void create(PreparedTask task) {
		try {
			todoService.create(task.todo());
			if (task.todo().getId() == null) {
				throw new IllegalStateException("登録されたタスクのIDを取得できませんでした。");
			}
		} catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
			throw new RegistrationFailure(task.line(), exception);
		}
	}

	private Validation validate(List<Row> rows) {
		Map<String, Long> categories = todoService.findAllCategories().stream()
				.collect(Collectors.toMap(Category::getName, Category::getId));
		Map<String, Long> assignees = todoService.findAllAssignees().stream()
				.collect(Collectors.toMap(Assignee::getName, Assignee::getId));
		Map<Long, PreparedTask> tasksById = new HashMap<>();
		List<PreparedTask> tasks = new ArrayList<>();
		List<Issue> errors = new ArrayList<>();
		List<Issue> warnings = new ArrayList<>();
		for (Row row : rows) {
			List<String> cells = row.cells();
			int line = row.line();
			if (cells.size() != TodoCsv.HEADER.size()) {
				errors.add(new Issue(line, "列数が9列ではありません。"));
				continue;
			}
			Long id = readId(cells.get(0), "ID", line, errors);
			Long parentId = readId(cells.get(1), "親ID", line, errors);
			Todo todo = new Todo();
			String title = cells.get(2);
			if (title.isBlank() || title.length() > 255) {
				errors.add(new Issue(line, "タスク名は必須です。255文字以内で入力してください。"));
			}
			todo.setTitle(title);
			todo.setCategoryId(findMasterId(categories, cells.get(3), "カテゴリ", line, errors));
			todo.setAssigneeId(findMasterId(assignees, cells.get(4), "担当者", line, errors));
			todo.setStartDate(readDate(cells.get(5), "開始日", line, errors));
			todo.setDueDate(readDate(cells.get(6), "期限", line, errors));
			try {
				todoService.validateDates(todo);
			} catch (IllegalArgumentException exception) {
				errors.add(new Issue(line, exception.getMessage()));
			}
			todo.setStatus(readStatus(cells.get(7), line, errors));
			Integer order = readOrder(cells.get(8), line, errors);
			for (int column = 2; column <= 4; column++) {
				if (TodoCsv.hasExportPrefix(cells.get(column))) {
					warnings.add(new Issue(line, TodoCsv.HEADER.get(column) + "の先頭にあるアポストロフィはそのまま扱います。"));
				}
			}
			PreparedTask task = new PreparedTask(line, id, parentId, order, todo);
			tasks.add(task);
			if (id != null) {
				PreparedTask first = tasksById.putIfAbsent(id, task);
				if (first != null) {
					errors.add(new Issue(line, "IDが" + first.line() + "行目と重複しています。"));
				}
			}
		}
		for (PreparedTask task : tasks) {
			validateParent(task, tasksById, errors);
		}
		errors.sort(Comparator.comparingInt(Issue::line));
		return new Validation(new Review(List.copyOf(errors), List.copyOf(warnings)), tasks);
	}

	private void validateParent(PreparedTask task, Map<Long, PreparedTask> tasksById, List<Issue> errors) {
		Long parentId = task.parentId();
		if (parentId == null) {
			return;
		}
		PreparedTask parent = tasksById.get(parentId);
		if (parentId.equals(task.id())) {
			errors.add(new Issue(task.line(), "自分自身を親タスクには指定できません。"));
		} else if (parent == null) {
			errors.add(new Issue(task.line(), "親IDに対応する行がCSV内にありません。親タスクも含めてください。"));
		} else if (parent.parentId() != null) {
			Set<Long> visited = new HashSet<>();
			PreparedTask ancestor = parent;
			while (ancestor != null && ancestor.parentId() != null) {
				if (!visited.add(ancestor.id())) {
					errors.add(new Issue(task.line(), "親IDの参照が循環しています。"));
					return;
				}
				ancestor = tasksById.get(ancestor.parentId());
			}
			errors.add(new Issue(task.line(), "子タスクを親には指定できません。親子2階層までです。"));
		}
	}

	private Long readId(String value, String label, int line, List<Issue> errors) {
		if (value.isEmpty()) {
			return null;
		}
		try {
			long id = Long.parseLong(value);
			if (id > 0 && value.charAt(0) != '+') {
				return id;
			}
		} catch (NumberFormatException exception) {
			// Report the same input error for values outside the supported ID range.
		}
		errors.add(new Issue(line, label + "は正の整数か空欄にしてください。"));
		return null;
	}

	private Integer readOrder(String value, int line, List<Issue> errors) {
		if (value.isEmpty()) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException exception) {
			errors.add(new Issue(line, "表示順は整数か空欄にしてください。"));
			return null;
		}
	}

	private Long findMasterId(Map<String, Long> master, String name, String label, int line, List<Issue> errors) {
		if (name.isEmpty()) {
			return null;
		}
		Long id = master.get(name);
		if (id == null) {
			errors.add(new Issue(line, "登録済みの" + label + "に同じ名称がありません。"));
		}
		return id;
	}

	private LocalDate readDate(String value, String label, int line, List<Issue> errors) {
		try {
			return TodoService.parseDate(value, label);
		} catch (IllegalArgumentException exception) {
			errors.add(new Issue(line, exception.getMessage()));
			return null;
		}
	}

	private TodoStatus readStatus(String value, int line, List<Issue> errors) {
		for (TodoStatus status : TodoStatus.values()) {
			if (status.getLabel().equals(value)) {
				return status;
			}
		}
		errors.add(new Issue(line, "ステータスは未着手・進行中・完了のいずれかを指定してください。"));
		return null;
	}
}
