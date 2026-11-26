package com.example.demo.config;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpBearerTokenFilterTest {

	private final McpBearerTokenFilter filter = new McpBearerTokenFilter("test-local-token");

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void acceptsALoopbackRequestWithTheConfiguredBearerToken() throws Exception {
		MockHttpServletRequest request = loopbackRequest();
		request.addHeader("Authorization", "Bearer test-local-token");
		AtomicReference<String> principal = new AtomicReference<>();

		filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
				principal.set(SecurityContextHolder.getContext().getAuthentication().getName()));

		assertEquals("mcp-local", principal.get());
	}

	@Test
	void rejectsMissingOrInvalidTokens() throws Exception {
		MockHttpServletResponse missingResponse = new MockHttpServletResponse();
		filter.doFilter(loopbackRequest(), missingResponse, (ignoredRequest, ignoredResponse) -> { });
		assertEquals(401, missingResponse.getStatus());

		MockHttpServletRequest invalidRequest = loopbackRequest();
		invalidRequest.addHeader("Authorization", "Bearer wrong-token");
		MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
		filter.doFilter(invalidRequest, invalidResponse, (ignoredRequest, ignoredResponse) -> { });
		assertEquals(401, invalidResponse.getStatus());

		MockHttpServletResponse unconfiguredResponse = new MockHttpServletResponse();
		new McpBearerTokenFilter("").doFilter(loopbackRequest(), unconfiguredResponse,
				(ignoredRequest, ignoredResponse) -> { });
		assertEquals(401, unconfiguredResponse.getStatus());
	}

	@Test
	void rejectsNonLoopbackRequestsAndUnexpectedOrigins() throws Exception {
		MockHttpServletRequest remoteRequest = loopbackRequest();
		remoteRequest.setRemoteAddr("192.0.2.10");
		remoteRequest.addHeader("Authorization", "Bearer test-local-token");
		MockHttpServletResponse remoteResponse = new MockHttpServletResponse();
		filter.doFilter(remoteRequest, remoteResponse, (ignoredRequest, ignoredResponse) -> { });
		assertEquals(403, remoteResponse.getStatus());

		MockHttpServletRequest originRequest = loopbackRequest();
		originRequest.addHeader("Authorization", "Bearer test-local-token");
		originRequest.addHeader("Origin", "https://untrusted.example");
		MockHttpServletResponse originResponse = new MockHttpServletResponse();
		filter.doFilter(originRequest, originResponse, (ignoredRequest, ignoredResponse) -> { });
		assertEquals(403, originResponse.getStatus());
		assertNull(SecurityContextHolder.getContext().getAuthentication());
	}

	private MockHttpServletRequest loopbackRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
		request.setRemoteAddr("127.0.0.1");
		return request;
	}
}
