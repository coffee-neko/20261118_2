package com.example.demo.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JapaneseHolidayServiceTest {

	private final AtomicInteger requestCount = new AtomicInteger();
	private final Map<String, ApiResponse> responses = new ConcurrentHashMap<>();
	private HttpServer server;
	private JapaneseHolidayService holidayService;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/v1/", this::respond);
		server.start();
		holidayService = new JapaneseHolidayService(new ObjectMapper(),
				URI.create("http://localhost:" + server.getAddress().getPort() + "/api/v1/"));
	}

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void obtainsDatesAndNamesAcrossYearsAndCachesEachYear() {
		responses.put("/api/v1/2026/date.json", new ApiResponse(200,
				"{\"2026-12-23\":\"天皇誕生日\"}"));
		responses.put("/api/v1/2027/date.json", new ApiResponse(200,
				"{\"2027-01-01\":\"元日\"}"));

		JapaneseHolidayService.HolidayPeriod holidays = holidayService.findHolidays(
				LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 3));

		assertEquals("天皇誕生日", holidays.holidays().get(LocalDate.of(2026, 12, 23)));
		assertEquals("元日", holidays.holidays().get(LocalDate.of(2027, 1, 1)));
		assertTrue(holidays.unavailableYears().isEmpty());
		assertEquals(2, requestCount.get());

		holidayService.findHolidays(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 3));
		assertEquals(2, requestCount.get());
	}

	@Test
	void treatsAnUnavailableYearAsUnknownAndCachesTheFailure() {
		responses.put("/api/v1/2026/date.json", new ApiResponse(503, "temporary outage"));

		JapaneseHolidayService.HolidayPeriod holidays = holidayService.findHolidays(
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

		assertTrue(holidays.holidays().isEmpty());
		assertTrue(holidays.isUnknown(LocalDate.of(2026, 1, 1)));
		assertFalse(holidays.unavailableYears().isEmpty());
		assertEquals(1, requestCount.get());

		holidayService.findHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
		assertEquals(1, requestCount.get());
	}

	@Test
	void convertsInstantsToJapaneseDatesBeforeLookingUpHolidays() {
		responses.put("/api/v1/2027/date.json", new ApiResponse(200,
				"{\"2027-01-01\":\"元日\"}"));

		JapaneseHolidayService.HolidayPeriod holidays = holidayService.findHolidays(
				Instant.parse("2026-12-31T15:00:00Z"), Instant.parse("2026-12-31T15:00:00Z"));

		assertEquals("元日", holidays.holidays().get(LocalDate.of(2027, 1, 1)));
	}

	private void respond(HttpExchange exchange) throws IOException {
		requestCount.incrementAndGet();
		ApiResponse response = responses.getOrDefault(exchange.getRequestURI().getPath(),
				new ApiResponse(404, "not found"));
		byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(response.status(), body.length);
		try (var output = exchange.getResponseBody()) {
			output.write(body);
		}
	}

	private record ApiResponse(int status, String body) {
	}
}
