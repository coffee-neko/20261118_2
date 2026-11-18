package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(TodoController.class)
class TodoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void todoListReturnsListView() throws Exception {
		mockMvc.perform(get("/todo"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/list"))
				.andExpect(model().attributeExists("todos"));
	}

	@Test
	void newTodoReturnsNewView() throws Exception {
		mockMvc.perform(get("/todo/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/new"));
	}

	@Test
	void confirmReturnsConfirmView() throws Exception {
		mockMvc.perform(get("/todo/confirm"))
				.andExpect(status().isOk())
				.andExpect(view().name("todo/confirm"));
	}
}
