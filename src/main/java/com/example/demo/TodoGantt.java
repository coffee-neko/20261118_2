package com.example.demo;

import java.time.LocalDate;
import java.time.YearMonth;

import com.example.demo.model.Todo;

public final class TodoGantt {

	static final YearMonth FIRST_MONTH = YearMonth.of(1, 1);
	static final YearMonth LAST_MONTH = YearMonth.of(9999, 12);

	private TodoGantt() {
	}

	static YearMonth parseMonth(String value) {
		if (!value.matches("[0-9]{4}-[0-9]{2}")) {
			throw new IllegalArgumentException("Expected yyyy-MM");
		}
		YearMonth month = YearMonth.parse(value);
		if (month.isBefore(FIRST_MONTH) || month.isAfter(LAST_MONTH)) {
			throw new IllegalArgumentException("Month is outside the supported range");
		}
		return month;
	}

	static Placement place(Todo todo, YearMonth month) {
		LocalDate start = todo.getStartDate();
		LocalDate due = todo.getDueDate();
		if (start == null && due == null) {
			return Placement.withoutMark(Kind.UNSCHEDULED);
		}
		if (start == null || due == null) {
			LocalDate date = start == null ? due : start;
			if (!YearMonth.from(date).equals(month)) {
				return Placement.withoutMark(Kind.OUTSIDE);
			}
			Kind kind = start == null ? Kind.DUE : Kind.START;
			return new Placement(kind, date.getDayOfMonth(), 1, false, false);
		}
		if (start.isAfter(due)) {
			return Placement.withoutMark(Kind.INVALID);
		}

		LocalDate firstDay = month.atDay(1);
		LocalDate lastDay = month.atEndOfMonth();
		if (due.isBefore(firstDay) || start.isAfter(lastDay)) {
			return Placement.withoutMark(Kind.OUTSIDE);
		}
		boolean continuesBefore = start.isBefore(firstDay);
		boolean continuesAfter = due.isAfter(lastDay);
		int firstColumn = continuesBefore ? 1 : start.getDayOfMonth();
		int lastColumn = continuesAfter ? lastDay.getDayOfMonth() : due.getDayOfMonth();
		return new Placement(Kind.BAR, firstColumn, lastColumn - firstColumn + 1, continuesBefore, continuesAfter);
	}

	public enum Kind {
		BAR, START, DUE, UNSCHEDULED, OUTSIDE, INVALID
	}

	public record Placement(Kind kind, int column, int span, boolean continuesBefore, boolean continuesAfter) {

		private static Placement withoutMark(Kind kind) {
			return new Placement(kind, 0, 0, false, false);
		}
	}
}
