package com.example.demo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoLocalSafetyGuardTest {

	@Test
	void allowsLoopbackOnlyWhenMcpIsDisabled() {
		assertDoesNotThrow(() -> DemoLocalSafetyGuard.validate("127.0.0.1", false));
		assertDoesNotThrow(() -> DemoLocalSafetyGuard.validate("::1", false));
	}

	@Test
	void rejectsNonLoopbackBinding() {
		assertThrows(IllegalStateException.class, () -> DemoLocalSafetyGuard.validate("0.0.0.0", false));
	}

	@Test
	void rejectsMcpInTheDemoProfile() {
		assertThrows(IllegalStateException.class, () -> DemoLocalSafetyGuard.validate("127.0.0.1", true));
	}
}
