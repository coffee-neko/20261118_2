package com.example.demo.mapper;

import java.util.List;

import com.example.demo.model.Todo;
import org.apache.ibatis.annotations.Param;

public interface TodoMapper {

	List<Todo> findAll();

	List<Todo> findPage(@Param("keyword") String keyword, @Param("completed") Boolean completed,
			@Param("categoryId") Long categoryId,
			@Param("limit") int limit, @Param("offset") int offset);

	long countByCondition(@Param("keyword") String keyword, @Param("completed") Boolean completed,
			@Param("categoryId") Long categoryId);

	Todo findById(Long id);

	Integer findMaxDisplayOrder();

	List<Todo> findPageForUpdate(@Param("limit") int limit, @Param("offset") int offset);

	int insert(Todo todo);

	int update(Todo todo);

	int updateDisplayOrder(@Param("id") Long id, @Param("displayOrder") int displayOrder);

	int deleteById(Long id);
}
