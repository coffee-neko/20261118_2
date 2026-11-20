package com.example.demo.mapper;

import java.util.List;

import com.example.demo.model.Todo;
import com.example.demo.model.TodoProgress;
import com.example.demo.model.TodoStatus;
import org.apache.ibatis.annotations.Param;

public interface TodoMapper {

	List<Todo> findAll();

	List<Todo> findForKanban();

	List<Todo> findPage(@Param("keyword") String keyword, @Param("status") TodoStatus status,
			@Param("categoryId") Long categoryId, @Param("assigneeId") Long assigneeId,
			@Param("limit") int limit, @Param("offset") long offset);

	long countByCondition(@Param("keyword") String keyword, @Param("status") TodoStatus status,
			@Param("categoryId") Long categoryId, @Param("assigneeId") Long assigneeId);

	TodoProgress findQuestProgress();

	Todo findById(Long id);

	Long lockById(Long id);

	List<Todo> findParentOptions(@Param("taskId") Long taskId);

	boolean hasChildren(@Param("parentId") Long parentId);

	Integer findMaxDisplayOrder();

	int insert(Todo todo);

	int update(Todo todo);

	int updateStatus(@Param("id") Long id, @Param("status") TodoStatus status);

	int updateDisplayOrder(@Param("id") Long id, @Param("displayOrder") int displayOrder);

	int deleteById(Long id);
}
