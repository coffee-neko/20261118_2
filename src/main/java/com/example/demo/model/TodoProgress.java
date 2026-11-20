package com.example.demo.model;

public record TodoProgress(long total, long completed) {

	public int percentage() {
		return percentage(total, completed);
	}

	static int percentage(long total, long completed) {
		return total == 0 ? 0 : (int) Math.round(100.0 * completed / total);
	}
}
