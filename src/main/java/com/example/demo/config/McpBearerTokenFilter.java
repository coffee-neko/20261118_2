package com.example.demo.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates only local MCP requests with a separately managed bearer token. */
public class McpBearerTokenFilter extends OncePerRequestFilter {

	private static final List<String> LOOPBACK_ADDRESSES = List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
	private final String expectedToken;

	public McpBearerTokenFilter(String expectedToken) {
		this.expectedToken = expectedToken == null ? "" : expectedToken;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())) {
			reject(response, HttpServletResponse.SC_FORBIDDEN, "MCP is available only from this computer.");
			return;
		}
		if (!isAllowedOrigin(request.getHeader("Origin"))) {
			reject(response, HttpServletResponse.SC_FORBIDDEN, "The MCP request origin is not allowed.");
			return;
		}
		String token = bearerToken(request.getHeader("Authorization"));
		if (expectedToken.isBlank() || token == null || !tokensMatch(expectedToken, token)) {
			reject(response, HttpServletResponse.SC_UNAUTHORIZED, "A valid MCP bearer token is required.");
			return;
		}

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("mcp-local", null,
				List.of(new SimpleGrantedAuthority("ROLE_MCP")));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		filterChain.doFilter(request, response);
	}

	private static String bearerToken(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return null;
		}
		String token = authorization.substring("Bearer ".length()).trim();
		return token.isEmpty() ? null : token;
	}

	private static boolean tokensMatch(String expected, String actual) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean isAllowedOrigin(String origin) {
		if (origin == null || origin.isBlank()) {
			return true;
		}
		try {
			URI uri = URI.create(origin);
			return "http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost())
					|| "http".equalsIgnoreCase(uri.getScheme()) && LOOPBACK_ADDRESSES.contains(uri.getHost());
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static void reject(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"" + message + "\"}");
	}
}
