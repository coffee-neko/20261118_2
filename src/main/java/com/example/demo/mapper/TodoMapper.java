package com.example.demo.mapper;

import java.util.List;

import com.example.demo.model.Todo;
import org.apache.ibatis.annotations.Param;

public interface TodoMapper {

	List<Todo> findAll();

	List<Todo> findPage(@Param("limit") int limit, @Param("offset") int offset);

	long countAll();

	Todo findById(Long id);

	int insert(Todo todo);

	int update(Todo todo);

	int deleteById(Long id);
}
