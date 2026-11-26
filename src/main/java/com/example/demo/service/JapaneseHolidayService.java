package com.example.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Provides Japanese public holidays for calendar-oriented features.
 *
 * <p>Data is obtained per year from Holidays JP and retained for the lifetime of
 * the application. A failed year is retained as unavailable so an outage does
 * not repeatedly delay normal application use.</p>
 */
@Service
public class JapaneseHolidayService {

	public static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");
	private static final Logger logger = LoggerFactory.getLogger(JapaneseHolidayService.class);
	private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
	private static final int READ_TIMEOUT_MILLIS = 5_000;
	private static final URI DEFAULT_API_BASE_URI = URI.create("https://holidays-jp.github.io/api/v1/");

	private final ObjectMapper objectMapper;
	private final URI apiBaseUri;
	private final ConcurrentHashMap<Integer, YearHolidays> cache = new ConcurrentHashMap<>();

	public JapaneseHolidayService(ObjectMapper objectMapper) {
		this(objectMapper, DEFAULT_API_BASE_URI);
	}

	JapaneseHolidayService(ObjectMapper objectMapper, URI apiBaseUri) {
		this.objectMapper = objectMapper;
		String base = apiBaseUri.toString();
		this.apiBaseUri = URI.create(base.endsWith("/") ? base : base + "/");
	}

	/**
	 * Returns holiday names in the inclusive period. Dates without source data are
	 * reported as unknown through {@link HolidayPeriod#unavailableYears()}.
	 */
	public HolidayPeriod findHolidays(LocalDate startInclusive, LocalDate endInclusive) {
		if (startInclusive == null || endInclusive == null || startInclusive.isAfter(endInclusive)) {
			throw new IllegalArgumentException("The holiday period must have a valid start and end date.");
		}

		Map<LocalDate, String> holidays = new LinkedHashMap<>();
		Set<Integer> unavailableYears = ConcurrentHashMap.newKeySet();
		int year = startInclusive.getYear();
		while (true) {
			YearHolidays yearHolidays = cache.computeIfAbsent(year, this::loadYear);
			if (!yearHolidays.available()) {
				unavailableYears.add(year);
			} else {
				yearHolidays.dates().forEach((date, name) -> {
					if (!date.isBefore(startInclusive) && !date.isAfter(endInclusive)) {
						holidays.put(date, name);
					}
				});
			}
			if (year == endInclusive.getYear()) {
				break;
			}
			year++;
		}
		return new HolidayPeriod(Map.copyOf(holidays), Set.copyOf(unavailableYears));
	}

	/**
	 * Converts instants to Japanese calendar dates before looking up holidays.
	 */
	public HolidayPeriod findHolidays(Instant startInclusive, Instant endInclusive) {
		if (startInclusive == null || endInclusive == null || startInclusive.isAfter(endInclusive)) {
			throw new IllegalArgumentException("The holiday period must have a valid start and end time.");
		}
		return findHolidays(LocalDate.ofInstant(startInclusive, JAPAN_ZONE),
				LocalDate.ofInstant(endInclusive, JAPAN_ZONE));
	}

	private YearHolidays loadYear(int year) {
		try {
			return new YearHolidays(fetchYear(year), true);
		} catch (IOException | RuntimeException exception) {
			logger.warn("Could not retrieve Japanese holidays for {}. The year will be treated as unavailable.", year,
					exception);
			return new YearHolidays(Map.of(), false);
		}
	}

	private Map<LocalDate, String> fetchYear(int year) throws IOException {
		HttpURLConnection connection = null;
		try {
			URI requestUri = apiBaseUri.resolve(year + "/date.json");
			connection = (HttpURLConnection) requestUri.toURL().openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "application/json");
			connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
			connection.setReadTimeout(READ_TIMEOUT_MILLIS);

			int status = connection.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				throw new IOException("Holidays JP returned HTTP " + status + " for " + year + ".");
			}
			try (InputStream response = connection.getInputStream()) {
				JsonNode body = objectMapper.readTree(response);
				if (body == null || !body.isObject()) {
					throw new IOException("Holidays JP returned an invalid JSON object for " + year + ".");
				}
				Map<LocalDate, String> dates = new HashMap<>();
				for (Map.Entry<String, JsonNode> entry : body.properties()) {
					try {
						dates.put(LocalDate.parse(entry.getKey()), entry.getValue().asString());
					} catch (DateTimeParseException exception) {
						logger.warn("Ignoring an invalid holiday date from Holidays JP: {}", entry.getKey());
					}
				}
				return Map.copyOf(dates);
			}
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	public record HolidayPeriod(Map<LocalDate, String> holidays, Set<Integer> unavailableYears) {
		public boolean isUnknown(LocalDate date) {
			return unavailableYears.contains(date.getYear());
		}
	}

	private record YearHolidays(Map<LocalDate, String> dates, boolean available) {
	}
}
