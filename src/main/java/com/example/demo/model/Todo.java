package com.example.demo.model;

import lombok.Data;

@Data
public class Todo {

	private Long id;
	private String title;
	private Boolean completed;
	private Integer displayOrder;
	private Long categoryId;
	private String categoryName;
	private String categoryColor;
}
