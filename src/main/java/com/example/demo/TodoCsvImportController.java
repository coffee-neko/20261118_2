package com.example.demo;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.demo.TodoCsvImportService.Issue;
import com.example.demo.TodoCsvReader.Row;
import com.example.demo.model.Todo;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/todo/import")
public class TodoCsvImportController {
	private static final Logger logger = LoggerFactory.getLogger(TodoCsvImportController.class);
	private static final String VIEW = "todo/import";
	private static final Duration PREVIEW_LIFETIME = Duration.ofMinutes(15);
	private static final int MAX_PENDING_PREVIEWS = 32;

	private record PendingImport(String username, String sessionId, Instant expiresAt, List<Row> rows) {}

	private final TodoCsvImportService importService;
	private final Map<String, PendingImport> pendingImports = new HashMap<>();

	public TodoCsvImportController(TodoCsvImportService importService) {
		this.importService = importService;
	}

	@GetMapping
	public String form() {
		return VIEW;
	}

	@GetMapping("/template.csv")
	public ResponseEntity<byte[]> template() {
		Todo parent = new Todo();
		parent.setId(1L);
		parent.setTitle("書類を確認する（記入例）");
		parent.setDisplayOrder(1);
		Todo child = new Todo();
		child.setId(2L);
		child.setParentId(1L);
		child.setTitle("不足書類を確認する（記入例）");
		child.setDisplayOrder(1);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename("nencho-import-template.csv").build().toString())
				.body(TodoCsv.encode(List.of(parent, child)));
	}

	@PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public String preview(@RequestParam(name = "file", required = false) MultipartFile file,
			Principal principal, HttpSession session, Model model) {
		if (file == null || file.isEmpty()) {
			return showError(model, 0, "CSVファイルを選択してください。");
		}
		if (file.getSize() > TodoCsvReader.MAX_BYTES) {
			return showError(model, 0, "CSVファイルは256 KB以内にしてください。");
		}
		try {
			List<Row> rows = TodoCsvReader.read(file.getBytes());
			var review = importService.preview(rows);
			showReview(model, rows, review.errors(), review.warnings());
			if (review.errors().isEmpty()) {
				String confirmationId = savePreview(principal.getName(), session.getId(), rows);
				if (confirmationId == null) {
					return showError(model, 0, "プレビューの保管件数が上限に達しました。時間をおいて再度お試しください。");
				}
				model.addAttribute("confirmationId", confirmationId);
			}
			return VIEW;
		} catch (TodoCsvReader.InvalidCsv exception) {
			return showError(model, exception.line(), exception.getMessage());
		} catch (IOException exception) {
			return showError(model, 0, "CSVファイルを読み込めませんでした。");
		} catch (DataAccessException exception) {
			logger.warn("Could not load CSV import master data", exception);
			return showError(model, 0, "カテゴリ・担当者の情報を確認できませんでした。再度お試しください。");
		}
	}

	@PostMapping("/confirm")
	public String confirm(@RequestParam(name = "confirmationId", defaultValue = "") String confirmationId,
			Principal principal, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
		PendingImport pending = takePreview(confirmationId, principal.getName(), session.getId());
		if (pending == null) {
			return showError(model, 0, "確認IDが無効、期限切れ、または使用済みです。必要に応じてCSVを選択し直してください。");
		}
		try {
			var review = importService.register(pending.rows());
			if (!review.errors().isEmpty()) {
				showReview(model, pending.rows(), review.errors(), review.warnings());
				return VIEW;
			}
		} catch (TodoCsvImportService.RegistrationFailure exception) {
			logger.warn("CSV import registration failed on line {}", exception.line(), exception);
			return showError(model, exception.line(), exception.getMessage());
		} catch (DataAccessException | TransactionException exception) {
			logger.warn("Could not complete the CSV import transaction", exception);
			return showError(model, 0, "CSVインポートを完了できませんでした。一覧で登録状況を確認してください。");
		}
		redirectAttributes.addFlashAttribute("successMessage", "CSVから" + pending.rows().size() + "件を登録しました。");
		return "redirect:/todo";
	}

	private synchronized String savePreview(String username, String sessionId, List<Row> rows) {
		Instant now = Instant.now();
		pendingImports.values().removeIf(pending -> !pending.expiresAt().isAfter(now));
		if (pendingImports.size() >= MAX_PENDING_PREVIEWS) {
			return null;
		}
		String id = UUID.randomUUID().toString();
		pendingImports.put(id, new PendingImport(username, sessionId, now.plus(PREVIEW_LIFETIME), rows));
		return id;
	}

	private synchronized PendingImport takePreview(String id, String username, String sessionId) {
		Instant now = Instant.now();
		pendingImports.values().removeIf(pending -> !pending.expiresAt().isAfter(now));
		PendingImport pending = pendingImports.get(id);
		if (pending == null || !pending.username().equals(username) || !pending.sessionId().equals(sessionId)) {
			return null;
		}
		// Claim before registration so concurrent submissions cannot start a second transaction.
		return pendingImports.remove(id);
	}

	private void showReview(Model model, List<Row> rows, List<Issue> errors, List<Issue> warnings) {
		model.addAttribute("rowCount", rows.size());
		model.addAttribute("rows", rows.stream().filter(row -> row.cells().size() == TodoCsv.HEADER.size()).toList());
		model.addAttribute("headers", TodoCsv.HEADER);
		model.addAttribute("errors", errors);
		model.addAttribute("warnings", warnings);
	}

	private String showError(Model model, int line, String message) {
		model.addAttribute("errors", List.of(new Issue(line, message)));
		return VIEW;
	}
}
