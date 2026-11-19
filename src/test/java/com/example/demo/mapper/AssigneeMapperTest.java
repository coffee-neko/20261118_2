package com.example.demo.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AssigneeMapperTest {

	@Autowired
	private AssigneeMapper assigneeMapper;

	@Test
	void findsTheFourInitialAssignees() {
		assertEquals(4, assigneeMapper.findAll().size());
		assertNotNull(assigneeMapper.findById(1L));
	}
}
