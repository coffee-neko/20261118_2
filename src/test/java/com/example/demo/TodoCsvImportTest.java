package com.example.demo;

import java.nio.charset.StandardCharsets;

import com.example.demo.mapper.TodoMapper;
import com.example.demo.model.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todoCsvImportTest")
@AutoConfigureMockMvc
class TodoCsvImportTest {
	private static final String HEADER = "ID,親ID,タスク名,カテゴリ,担当者,開始日,期限,ステータス,表示順\r\n";
	private static final String PARENT_TITLE = "CSV親,\"年調\"\r\n確認";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TodoMapper todoMapper;

	@Test
	void previewAndConfirmKeepHierarchyAndConsumeConfirmationOnlyOnce() throws Exception {
		MockHttpSession session = new MockHttpSession();
		int previousCount = todoMapper.findAll().size();
		String data = "\uFEFF" + HEADER
				+ "92,90,CSV取込の子（後）,事前準備,たろう,2026-11-23,2026-11-24,進行中,2\r\n"
				+ "90,,\"CSV親,\"\"年調\"\"\r\n確認\",,,,,未着手,1\r\n"
				+ "91,90,CSV取込の子（先）,,,,,完了,1";
		var preview = mockMvc.perform(multipart("/todo/import/preview").file(csv(data))
				.session(session).with(user("importer")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("confirmationId"))
				.andExpect(content().string(containsString("プレビュー（3件）")))
				.andReturn();
		String confirmationId = (String) preview.getModelAndView().getModel().get("confirmationId");
		assertEquals(previousCount, todoMapper.findAll().size());

		mockMvc.perform(post("/todo/import/confirm").session(session).with(user("other")).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(content().string(containsString("確認IDが無効")));
		mockMvc.perform(post("/todo/import/confirm").session(session).with(user("importer")).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(redirectedUrl("/todo"))
				.andExpect(flash().attribute("successMessage", "CSVから3件を登録しました。"));
		mockMvc.perform(post("/todo/import/confirm").session(session).with(user("importer")).with(csrf())
				.param("confirmationId", confirmationId))
				.andExpect(content().string(containsString("確認IDが無効")));

		var todos = todoMapper.findAll();
		assertEquals(previousCount + 3, todos.size());
		Todo parent = todos.stream().filter(todo -> todo.getTitle().equals(PARENT_TITLE)).findFirst().orElseThrow();
		Todo first = todos.stream().filter(todo -> todo.getTitle().equals("CSV取込の子（先）")).findFirst().orElseThrow();
		Todo last = todos.stream().filter(todo -> todo.getTitle().equals("CSV取込の子（後）")).findFirst().orElseThrow();
		assertNotEquals(90L, parent.getId());
		assertEquals(parent.getId(), first.getParentId());
		assertEquals(parent.getId(), last.getParentId());
		assertTrue(first.getDisplayOrder() < last.getDisplayOrder());
		assertEquals("事前準備", last.getCategoryName());
		assertEquals("たろう", last.getAssigneeName());
	}

	@Test
	void reportsPhysicalLineAfterQuotedNewlineWithoutAllowingConfirmation() throws Exception {
		int previousCount = todoMapper.findAll().size();
		String data = HEADER + "1,,\"一行目\n二行目\",,,,,未着手,\n"
				+ "2,99,親不足,,,2026-02-30,,進行中,\n";
		mockMvc.perform(multipart("/todo/import/preview").file(csv(data)).with(user("importer")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(model().attributeDoesNotExist("confirmationId"))
				.andExpect(content().string(containsString("4行目")))
				.andExpect(content().string(containsString("実在する日付")))
				.andExpect(content().string(containsString("親IDに対応する行がCSV内にありません")))
				.andExpect(content().string(not(containsString("を新規登録する"))));
		assertEquals(previousCount, todoMapper.findAll().size());
	}

	@Test
	void uploadAndConfirmationRequireCsrfToken() throws Exception {
		mockMvc.perform(multipart("/todo/import/preview").file(csv(HEADER)).with(user("importer")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/todo/import/confirm").with(user("importer")))
				.andExpect(status().isForbidden());
	}

	private MockMultipartFile csv(String value) {
		return new MockMultipartFile("file", "todos.csv", "text/csv", value.getBytes(StandardCharsets.UTF_8));
	}
}
