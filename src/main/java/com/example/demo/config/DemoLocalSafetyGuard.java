package com.example.demo.config;

import java.util.Set;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Prevents the public demonstration account from being started outside this computer. */
@Component
@Profile("demo-local")
public class DemoLocalSafetyGuard {

	private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost");

	private final String serverAddress;
	private final boolean mcpEnabled;

	public DemoLocalSafetyGuard(@Value("${server.address:}") String serverAddress,
			@Value("${spring.ai.mcp.server.enabled:false}") boolean mcpEnabled) {
		this.serverAddress = serverAddress;
		this.mcpEnabled = mcpEnabled;
	}

	@PostConstruct
	void validate() {
		validate(serverAddress, mcpEnabled);
	}

	static void validate(String serverAddress, boolean mcpEnabled) {
		if (!LOOPBACK_ADDRESSES.contains(serverAddress)) {
			throw new IllegalStateException("The demo-local profile may listen only on localhost.");
		}
		if (mcpEnabled) {
			throw new IllegalStateException("MCP must be disabled when the demo-local profile is active.");
		}
	}
}
