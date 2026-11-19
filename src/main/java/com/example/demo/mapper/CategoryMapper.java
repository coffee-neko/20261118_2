package com.example.demo.mapper;

import java.util.List;

import com.example.demo.model.Category;

public interface CategoryMapper {

	List<Category> findAll();

	Category findById(Long id);
}
