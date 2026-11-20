package com.example.demo.model;

import java.time.LocalDate;

import lombok.Data;

@Data
public class Todo {

	private Long id;
	private String title;
	private Boolean completed;
	private LocalDate startDate;
	private LocalDate dueDate;
	private Integer displayOrder;
	private Long categoryId;
	private String categoryName;
	private String categoryColor;
	private Long assigneeId;
	private String assigneeName;
}
