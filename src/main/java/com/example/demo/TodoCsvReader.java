package com.example.demo;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class TodoCsvReader {
	static final int MAX_BYTES = 256 * 1024;
	static final int MAX_ROWS = 500;

	record Row(int line, List<String> cells) {}

	static final class InvalidCsv extends IllegalArgumentException {
		private final int line;

		InvalidCsv(int line, String message) {
			super(message);
			this.line = line;
		}

		int line() {
			return line;
		}
	}

	private enum Field { START, UNQUOTED, QUOTED, CLOSED }

	private TodoCsvReader() {}

	static List<Row> read(byte[] bytes) {
		if (bytes.length > MAX_BYTES) {
			throw new InvalidCsv(0, "CSVファイルは256 KB以内にしてください。");
		}
		String csv;
		try {
			csv = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException exception) {
			throw new InvalidCsv(0, "CSVファイルをUTF-8で保存してください。");
		}

		List<Row> rows = new ArrayList<>();
		List<String> cells = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		Field field = Field.START;
		int line = 1;
		int rowStart = line;
		int start = csv.startsWith("\uFEFF") ? 1 : 0;
		for (int index = start; index < csv.length(); index++) {
			char ch = csv.charAt(index);
			if (ch == '\r' || ch == '\n') {
				int next = index + 1;
				if (ch == '\r' && next < csv.length() && csv.charAt(next) == '\n') {
					next++;
				}
				line++;
				if (field == Field.QUOTED) {
					value.append(csv, index, next);
				} else {
					finishRow(rows, cells, value, field, rowStart);
					field = Field.START;
					rowStart = line;
				}
				index = next - 1;
				continue;
			}
			if (ch == '"') {
				field = switch (field) {
					case START -> Field.QUOTED;
					case QUOTED -> Field.CLOSED;
					case CLOSED -> {
						value.append(ch);
						yield Field.QUOTED;
					}
					case UNQUOTED -> throw new InvalidCsv(rowStart, "ダブルクォートの位置が正しくありません。");
				};
			} else if (field == Field.QUOTED) {
				value.append(ch);
			} else if (ch == ',') {
				cells.add(value.toString());
				value.setLength(0);
				field = Field.START;
			} else if (field == Field.CLOSED) {
				throw new InvalidCsv(rowStart, "閉じダブルクォートの後に列の区切りが必要です。");
			} else {
				value.append(ch);
				field = Field.UNQUOTED;
			}
		}
		if (field == Field.QUOTED) {
			throw new InvalidCsv(rowStart, "ダブルクォートで囲んだセルが閉じていません。");
		}
		finishRow(rows, cells, value, field, rowStart);
		if (rows.isEmpty()) {
			throw new InvalidCsv(1, "CSVの見出し行がありません。");
		}
		Row header = rows.get(0);
		if (!TodoCsv.HEADER.equals(header.cells())) {
			throw new InvalidCsv(header.line(), "見出しはCSV出力またはテンプレートと同じ9列を同じ順番で指定してください。");
		}
		if (rows.size() == 1) {
			throw new InvalidCsv(0, "登録するタスクがありません。");
		}
		return List.copyOf(rows.subList(1, rows.size()));
	}

	private static void finishRow(List<Row> rows, List<String> cells, StringBuilder value, Field field, int line) {
		if (cells.isEmpty() && field == Field.START) {
			return;
		}
		if (rows.size() == MAX_ROWS + 1) {
			throw new InvalidCsv(line, "一度に取り込めるのは500件までです。");
		}
		cells.add(value.toString());
		rows.add(new Row(line, List.copyOf(cells)));
		cells.clear();
		value.setLength(0);
	}
}
