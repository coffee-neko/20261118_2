package com.example.demo.model;

import java.time.LocalDate;

import lombok.Data;

@Data
public class Todo {

	private Long id;
	private String title;
	private TodoStatus status = TodoStatus.NOT_STARTED;
	private LocalDate startDate;
	private LocalDate dueDate;
	private Integer displayOrder;
	private Long parentId;
	private String parentTitle;
	private long childCount;
	private long completedChildCount;
	private Long categoryId;
	private String categoryName;
	private String categoryColor;
	private Long assigneeId;
	private String assigneeName;

	public int getChildCompletionRate() {
		return TodoProgress.percentage(childCount, completedChildCount);
	}
}
