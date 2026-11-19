package com.example.demo.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CategoryMapperTest {

	@Autowired
	private CategoryMapper categoryMapper;

	@Test
	void findsTheSixInitialCategories() {
		assertEquals(6, categoryMapper.findAll().size());
		assertNotNull(categoryMapper.findById(1L));
	}
}
