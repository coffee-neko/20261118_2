package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.example.demo.model.Todo;

final class TodoCsv {

	private TodoCsv() {
	}

	static byte[] encode(List<Todo> todos) {
		StringBuilder csv = new StringBuilder("\uFEFF");
		appendRow(csv, "ID", "親ID", "タスク名", "カテゴリ", "担当者", "開始日", "期限", "ステータス", "表示順");
		for (Todo todo : todos) {
			appendRow(csv,
					Objects.toString(todo.getId(), ""),
					Objects.toString(todo.getParentId(), ""),
					safeText(todo.getTitle()),
					safeText(todo.getCategoryName()),
					safeText(todo.getAssigneeName()),
					Objects.toString(todo.getStartDate(), ""),
					Objects.toString(todo.getDueDate(), ""),
					todo.getStatus().getLabel(),
					Objects.toString(todo.getDisplayOrder(), ""));
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String safeText(String value) {
		String text = Objects.toString(value, "");
		// Check past leading whitespace and format characters that a spreadsheet may ignore.
		for (int index = 0; index < text.length();) {
			int character = text.codePointAt(index);
			if ("=+-@＝＋－＠".indexOf(character) >= 0 || Character.isISOControl(character)) {
				return "'" + text;
			}
			if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)
					&& Character.getType(character) != Character.FORMAT) {
				break;
			}
			index += Character.charCount(character);
		}
		return text;
	}

	private static void appendRow(StringBuilder csv, String... cells) {
		for (int index = 0; index < cells.length; index++) {
			if (index > 0) {
				csv.append(',');
			}
			csv.append('"').append(cells[index].replace("\"", "\"\"")).append('"');
		}
		csv.append("\r\n");
	}
}
