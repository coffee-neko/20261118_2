package com.example.demo;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.demo.model.TodoStatus;
import com.example.demo.service.TodoService;
import com.example.demo.service.TodoService.DeleteTarget;
import com.example.demo.service.TodoService.ListPage;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/todo/bulk")
public class TodoBulkController {
	private static final Logger logger = LoggerFactory.getLogger(TodoBulkController.class);
	private static final String PENDING_DELETION = TodoBulkController.class.getName() + ".pendingDeletion";
	private static final Duration CONFIRMATION_LIFETIME = Duration.ofMinutes(15);

	public record ListParameters(Integer page, String keyword, String status, Long categoryId, Long assigneeId) {
		public ListParameters {
			page = page == null ? 1 : page;
		}

		ListPage toListPage() {
			return new ListPage(page, keyword, TodoController.findStatus(status), categoryId, assigneeId);
		}
	}

	private record PendingDeletion(String id, String username, String sessionId, Instant expiresAt,
			ListParameters returnTo, List<DeleteTarget> targets) {}

	private final TodoService todoService;

	public TodoBulkController(TodoService todoService) {
		this.todoService = todoService;
	}

	@PostMapping("/status")
	public String updateStatus(@RequestParam(name = "ids", required = false) List<Long> ids,
			@RequestParam(name = "newStatus", defaultValue = "") String newStatus,
			@ModelAttribute("returnTo") ListParameters returnTo, RedirectAttributes redirectAttributes) {
		try {
			TodoStatus status = TodoController.requireStatus(newStatus);
			int count = todoService.updateSelectedStatus(ids, returnTo.toListPage(), status);
			redirectAttributes.addFlashAttribute("successMessage", count + "件のステータスを「" + status.getLabel() + "」に変更しました。");
		} catch (IllegalArgumentException | IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", "変更は0件です。" + exception.getMessage());
		} catch (DataAccessException | TransactionException exception) {
			logger.warn("Could not complete bulk status update", exception);
			redirectAttributes.addFlashAttribute("errorMessage", "一括変更を完了できませんでした。一覧で現在の状態を確認してください。");
		}
		return redirectToList(returnTo, redirectAttributes);
	}

	@PostMapping("/delete/preview")
	public String previewDeletion(@RequestParam(name = "ids", required = false) List<Long> ids,
			@ModelAttribute("returnTo") ListParameters returnTo, Principal principal, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			List<DeleteTarget> targets = todoService.previewSelectedDeletion(ids, returnTo.toListPage());
			model.addAttribute("targets", targets);
			model.addAttribute("confirmationId", saveDeletion(session, principal.getName(), returnTo, targets));
			return "todo/bulk-delete";
		} catch (IllegalArgumentException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", "削除は0件です。" + exception.getMessage());
		} catch (DataAccessException | TransactionException exception) {
			logger.warn("Could not preview bulk deletion", exception);
			redirectAttributes.addFlashAttribute("errorMessage", "削除は0件です。削除対象を確認できませんでした。");
		}
		return redirectToList(returnTo, redirectAttributes);
	}

	@PostMapping("/delete/confirm")
	public String confirmDeletion(@RequestParam(name = "confirmationId", defaultValue = "") String confirmationId,
			Principal principal, HttpSession session, RedirectAttributes redirectAttributes) {
		PendingDeletion pending = takeDeletion(session, principal.getName(), confirmationId);
		if (pending == null) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"確認が期限切れ、使用済み、または無効です。この送信での削除は0件です。");
			return "redirect:/todo";
		}
		try {
			int count = todoService.deleteSelected(pending.targets(), pending.returnTo().toListPage());
			redirectAttributes.addFlashAttribute("successMessage", count + "件のタスクを削除しました。");
		} catch (IllegalArgumentException | IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", "削除は0件です。" + exception.getMessage());
		} catch (DataAccessException | TransactionException exception) {
			logger.warn("Could not complete bulk deletion", exception);
			redirectAttributes.addFlashAttribute("errorMessage", "一括削除を完了できませんでした。一覧で現在の状態を確認してください。");
		}
		return redirectToList(pending.returnTo(), redirectAttributes);
	}

	private synchronized String saveDeletion(HttpSession session, String username, ListParameters returnTo,
			List<DeleteTarget> targets) {
		String id = UUID.randomUUID().toString();
		session.setAttribute(PENDING_DELETION, new PendingDeletion(id, username, session.getId(),
				Instant.now().plus(CONFIRMATION_LIFETIME), returnTo, targets));
		return id;
	}

	private synchronized PendingDeletion takeDeletion(HttpSession session, String username, String id) {
		PendingDeletion pending = (PendingDeletion) session.getAttribute(PENDING_DELETION);
		if (pending == null) {
			return null;
		}
		if (!pending.expiresAt().isAfter(Instant.now())) {
			session.removeAttribute(PENDING_DELETION);
			return null;
		}
		if (!pending.id().equals(id) || !pending.username().equals(username) || !pending.sessionId().equals(session.getId())) {
			return null;
		}
		// Consume the confirmation while holding the same lock for concurrent requests.
		session.removeAttribute(PENDING_DELETION);
		return pending;
	}

	private String redirectToList(ListParameters returnTo, RedirectAttributes attributes) {
		attributes.addAttribute("page", returnTo.page());
		if (returnTo.keyword() != null && !returnTo.keyword().isEmpty()) {
			attributes.addAttribute("keyword", returnTo.keyword());
		}
		if (TodoController.findStatus(returnTo.status()) != null) {
			attributes.addAttribute("status", returnTo.status());
		}
		if (returnTo.categoryId() != null) {
			attributes.addAttribute("categoryId", returnTo.categoryId());
		}
		if (returnTo.assigneeId() != null) {
			attributes.addAttribute("assigneeId", returnTo.assigneeId());
		}
		return "redirect:/todo";
	}
}
