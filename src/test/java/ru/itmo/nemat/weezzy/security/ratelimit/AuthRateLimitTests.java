package ru.itmo.nemat.weezzy.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
		"app.security.rate-limit.enabled=true",
		"app.security.rate-limit.login.capacity=2",
		"app.security.rate-limit.login.window=1m",
		"app.security.rate-limit.register.capacity=1",
		"app.security.rate-limit.register.window=1m",
		"app.security.rate-limit.email-resend.capacity=1",
		"app.security.rate-limit.email-resend.window=1m"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRateLimitTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine")
			.withExposedPorts(6379);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RedisAuthRateLimiter rateLimiter;

	@Autowired
	private AuthRateLimitProperties properties;

	@DynamicPropertySource
	static void infrastructureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Test
	void loginIsLimitedPerClientAddress() throws Exception {
		String clientAddress = "198.51.100.10";

		login(clientAddress)
				.andExpect(status().isBadRequest())
				.andExpect(header().string("X-RateLimit-Limit", "2"))
				.andExpect(header().string("X-RateLimit-Remaining", "1"));
		login(clientAddress)
				.andExpect(status().isBadRequest())
				.andExpect(header().string("X-RateLimit-Remaining", "0"));
		login(clientAddress)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("X-RateLimit-Limit", "2"))
				.andExpect(header().string("X-RateLimit-Remaining", "0"))
				.andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.error").value("Too Many Requests"))
				.andExpect(jsonPath("$.message")
						.value("Too many authentication attempts"))
				.andExpect(jsonPath("$.path").value("/api/auth/login"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void registrationLimitsAreIndependentBetweenClientAddresses() throws Exception {
		register("198.51.100.20", "rate-one@itmo.ru")
				.andExpect(status().isCreated());
		register("198.51.100.20", "rate-two@itmo.ru")
				.andExpect(status().isTooManyRequests());
		register("198.51.100.21", "rate-three@itmo.ru")
				.andExpect(status().isCreated());
	}

	@Test
	void emailVerificationResendIsRateLimited() throws Exception {
		String clientAddress = "198.51.100.30";

		resendEmailVerification(clientAddress)
				.andExpect(status().isAccepted())
				.andExpect(header().string("X-RateLimit-Remaining", "0"));
		resendEmailVerification(clientAddress)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.path").value("/api/auth/email/resend"));
	}

	@Test
	void redisScriptEnforcesCapacityAtomically() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(12);
		try {
			List<Callable<AuthRateLimitDecision>> attempts = IntStream
					.range(0, 20)
					.mapToObj(index -> (Callable<AuthRateLimitDecision>) () ->
							rateLimiter.consume(
									"login",
									"203.0.113.50",
									properties.login()
							))
					.toList();

			long allowed = executor.invokeAll(attempts).stream()
					.map(future -> {
						try {
							return future.get();
						} catch (Exception exception) {
							throw new AssertionError(exception);
						}
					})
					.filter(AuthRateLimitDecision::allowed)
					.count();

			assertThat(allowed).isEqualTo(properties.login().capacity());
		} finally {
			executor.shutdownNow();
		}
	}

	private ResultActions login(
			String clientAddress
	) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.with(from(clientAddress))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "missing-rate-user@itmo.ru",
						  "password": "password123"
						}
						"""));
	}

	private ResultActions register(
			String clientAddress,
			String email
	) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.with(from(clientAddress))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "password123"
						}
						""".formatted(email)));
	}

	private ResultActions resendEmailVerification(String clientAddress) throws Exception {
		return mockMvc.perform(post("/api/auth/email/resend")
				.with(from(clientAddress))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "missing-resend-rate-user@itmo.ru"
						}
						"""));
	}

	private RequestPostProcessor from(String clientAddress) {
		return request -> {
			request.setRemoteAddr(clientAddress);
			return request;
		};
	}
}
