package com.example.demo.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** View data for the monthly task calendar. */
public record TodoCalendar(YearMonth month, List<List<Day>> weeks, LocalDate selectedDate,
		List<Todo> selectedTasks, boolean holidayInformationUnavailable) {

	public record Day(LocalDate date, String holidayName, List<Todo> visibleTasks, int remainingTaskCount,
			boolean today, boolean selected) {
		public boolean hasTasks() {
			return !visibleTasks.isEmpty();
		}

		public boolean isSunday() {
			return date.getDayOfWeek() == DayOfWeek.SUNDAY;
		}

		public boolean isSaturday() {
			return date.getDayOfWeek() == DayOfWeek.SATURDAY;
		}
	}
}
