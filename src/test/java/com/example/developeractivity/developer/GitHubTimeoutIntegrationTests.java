package com.example.developeractivity.developer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.http.clients.connect-timeout=200ms",
		"spring.http.clients.read-timeout=100ms"
})
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@AutoConfigureMetrics
class GitHubTimeoutIntegrationTests {

	private static final CountDownLatch RELEASE_SLOW_RESPONSES = new CountDownLatch(1);
	private static final ExecutorService SERVER_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "github-test-server");
		thread.setDaemon(true);
		return thread;
	});
	private static final HttpServer GITHUB_SERVER = startGitHubServer();

	@Autowired
	private RestTestClient restTestClient;

	@DynamicPropertySource
	static void githubProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.http.serviceclient.github.base-url",
				() -> "http://localhost:" + GITHUB_SERVER.getAddress().getPort());
	}

	@AfterAll
	static void stopGitHubServer() {
		RELEASE_SLOW_RESPONSES.countDown();
		GITHUB_SERVER.stop(0);
		SERVER_EXECUTOR.shutdownNow();
	}

	@Test
	void returnsNormalGitHubResponseWithinTimeout() {
		restTestClient.get().uri("/developers/fast-user")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.username").isEqualTo("fast-user");
	}

	@Test
	void returnsGatewayTimeoutWhenProfileResponseIsDelayed() {
		restTestClient.get().uri("/developers/slow-user")
				.exchange()
				.expectStatus().isEqualTo(504)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Upstream service timed out")
				.jsonPath("$.detail").isEqualTo("GitHub API did not respond in time");
	}

	@Test
	void returnsGatewayTimeoutWhenRepositoryResponseIsDelayed() {
		restTestClient.get().uri("/developers/slow-user/repositories")
				.exchange()
				.expectStatus().isEqualTo(504)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Upstream service timed out");
	}

	@Test
	void keepsNotFoundResponseForMissingGitHubUser() {
		restTestClient.get().uri("/developers/missing-user")
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Developer not found");
	}

	@Test
	void returnsBadGatewayForNonTimeoutConnectionFailure() {
		restTestClient.get().uri("/developers/disconnected-user")
				.exchange()
				.expectStatus().isEqualTo(502)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Upstream service unavailable");
	}

	@Test
	void scrapesTimeoutAndUnavailableOutcomesAfterUpstreamFailures() {
		restTestClient.get().uri("/developers/slow-user")
				.exchange()
				.expectStatus().isEqualTo(504);
		restTestClient.get().uri("/developers/disconnected-user")
				.exchange()
				.expectStatus().isEqualTo(502);

		String body = restTestClient.get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("text/plain"))
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();
		assertThat(body)
				.contains("developer_github_calls_seconds_count{outcome=\"timeout\"")
				.contains("developer_github_calls_seconds_count{outcome=\"unavailable\"");
	}

	private static HttpServer startGitHubServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", GitHubTimeoutIntegrationTests::handleRequest);
			server.setExecutor(SERVER_EXECUTOR);
			server.start();
			return server;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to start GitHub test server", exception);
		}
	}

	private static void handleRequest(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if (path.startsWith("/users/slow-user")) {
			awaitRelease();
			exchange.close();
			return;
		}
		if (path.equals("/users/disconnected-user")) {
			exchange.close();
			return;
		}
		if (path.equals("/users/missing-user")) {
			sendJson(exchange, 404, "{\"message\":\"Not Found\"}");
			return;
		}
		if (path.equals("/users/fast-user")) {
			sendJson(exchange, 200, """
					{
					  "login": "fast-user",
					  "name": "Fast User",
					  "html_url": "https://github.com/fast-user",
					  "avatar_url": "https://avatars.githubusercontent.com/u/1",
					  "public_repos": 1,
					  "followers": 2
					}
					""");
			return;
		}
		sendJson(exchange, 500, "{\"message\":\"Unexpected request\"}");
	}

	private static void awaitRelease() {
		try {
			RELEASE_SLOW_RESPONSES.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
		byte[] content = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
		exchange.sendResponseHeaders(status, content.length);
		exchange.getResponseBody().write(content);
		exchange.close();
	}
}
