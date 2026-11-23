package com.example.demo.model;

import java.time.LocalDate;
import java.util.List;

public record TodoDashboard(LocalDate today, LocalDate lastDay, Summary summary, List<Todo> upcoming,
		List<Group> byAssignee, List<Group> byCategory) {

	public record Summary(long total, long notStarted, long inProgress, long completed, long overdue) {
		public long count(TodoStatus status) {
			return switch (status) {
				case NOT_STARTED -> notStarted;
				case IN_PROGRESS -> inProgress;
				case COMPLETED -> completed;
			};
		}

		public int percentage() {
			return TodoProgress.percentage(total, completed);
		}
	}

	public record Group(String name, long total, long completed) {
		public int percentage() {
			return TodoProgress.percentage(total, completed);
		}
	}
}
