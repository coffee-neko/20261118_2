package com.example.demo.model;

public enum TodoStatus {
	NOT_STARTED("未着手"),
	IN_PROGRESS("進行中"),
	COMPLETED("完了");

	private final String label;

	TodoStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean isCompleted() {
		return this == COMPLETED;
	}
}
