package com.example.demo.mapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;

import com.example.demo.model.TodoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoStatusConstraintTest {
	private static final String INSERT_TODO = "INSERT INTO todos (title, status, parent_id) VALUES (?, ?, ?)";

	@Test
	void validatesStatusesWhenInsertingChildrenAfterSchemaConnectionCloses() throws Exception {
		String url = "jdbc:h2:mem:todoStatusConstraintTest";
		// Keep the in-memory database alive while closing the connection that created the CHECK constraint.
		try (Connection keeper = DriverManager.getConnection(url)) {
			long parentId;
			try (Connection initializer = DriverManager.getConnection(url)) {
				ScriptUtils.executeSqlScript(initializer, new ClassPathResource("schema.sql"));
				try (PreparedStatement insertParent = initializer.prepareStatement(INSERT_TODO, Statement.RETURN_GENERATED_KEYS)) {
					insertParent.setString(1, "Parent");
					insertParent.setString(2, TodoStatus.NOT_STARTED.name());
					insertParent.setNull(3, Types.BIGINT);
					assertEquals(1, insertParent.executeUpdate());
					parentId = generatedId(insertParent);
				}
			}

			try (Connection writer = DriverManager.getConnection(url);
				 PreparedStatement insertChild = writer.prepareStatement(INSERT_TODO, Statement.RETURN_GENERATED_KEYS);
				 PreparedStatement readChild = keeper.prepareStatement("SELECT status, parent_id FROM todos WHERE id = ?")) {
				insertChild.setString(1, "Child");
				insertChild.setLong(3, parentId);
				for (TodoStatus status : TodoStatus.values()) {
					insertChild.setString(2, status.name());
					assertEquals(1, insertChild.executeUpdate());
					readChild.setLong(1, generatedId(insertChild));
					try (ResultSet child = readChild.executeQuery()) {
						assertTrue(child.next());
						assertEquals(status.name(), child.getString("status"));
						assertEquals(parentId, child.getLong("parent_id"));
					}
				}

				insertChild.setString(2, "INVALID");
				var rejected = assertThrows(SQLIntegrityConstraintViolationException.class, insertChild::executeUpdate);
				assertEquals("23513", rejected.getSQLState());
			}
		}
	}

	private static long generatedId(PreparedStatement insert) throws SQLException {
		try (ResultSet keys = insert.getGeneratedKeys()) {
			assertTrue(keys.next(), "Expected a generated task ID");
			return keys.getLong(1);
		}
	}
}
