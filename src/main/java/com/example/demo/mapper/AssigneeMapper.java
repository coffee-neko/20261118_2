package com.example.demo.mapper;

import java.util.List;

import com.example.demo.model.Assignee;

public interface AssigneeMapper {

	List<Assignee> findAll();

	Assignee findById(Long id);
}
