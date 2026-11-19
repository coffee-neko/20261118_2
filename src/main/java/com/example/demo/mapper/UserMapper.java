package com.example.demo.mapper;

import com.example.demo.model.AppUser;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

	AppUser findByUsername(@Param("username") String username);

	int insert(AppUser user);
}
