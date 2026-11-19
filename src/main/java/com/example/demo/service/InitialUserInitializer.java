package com.example.demo.service;

import com.example.demo.mapper.UserMapper;
import com.example.demo.model.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class InitialUserInitializer implements ApplicationRunner {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final String initialUsername;
	private final String initialPassword;

	public InitialUserInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder,
			@Value("${APP_INITIAL_USERNAME:}") String initialUsername,
			@Value("${APP_INITIAL_PASSWORD:}") String initialPassword) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.initialUsername = initialUsername;
		this.initialPassword = initialPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (initialUsername.isBlank() || initialPassword.isBlank()) {
			throw new IllegalStateException("APP_INITIAL_USERNAME and APP_INITIAL_PASSWORD must be set.");
		}
		if (userMapper.findByUsername(initialUsername) != null) {
			return;
		}

		AppUser user = new AppUser();
		user.setUsername(initialUsername);
		user.setPassword(passwordEncoder.encode(initialPassword));
		user.setRole("ROLE_USER");
		userMapper.insert(user);
	}
}
