package com.example.developeractivity.developer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.http.clients.connect-timeout=200ms",
		"spring.http.clients.read-timeout=100ms"
})
@AutoConfigureMockMvc
class GitHubTimeoutIntegrationTests {

	private static final CountDownLatch RELEASE_SLOW_RESPONSES = new CountDownLatch(1);
	private static final ExecutorService SERVER_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "github-test-server");
		thread.setDaemon(true);
		return thread;
	});
	private static final HttpServer GITHUB_SERVER = startGitHubServer();

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void githubProperties(DynamicPropertyRegistry registry) {
		registry.add("github.api.base-url", () -> "http://localhost:" + GITHUB_SERVER.getAddress().getPort());
	}

	@AfterAll
	static void stopGitHubServer() {
		RELEASE_SLOW_RESPONSES.countDown();
		GITHUB_SERVER.stop(0);
		SERVER_EXECUTOR.shutdownNow();
	}

	@Test
	void returnsNormalGitHubResponseWithinTimeout() throws Exception {
		mockMvc.perform(get("/developers/fast-user"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("fast-user"));
	}

	@Test
	void returnsGatewayTimeoutWhenProfileResponseIsDelayed() throws Exception {
		mockMvc.perform(get("/developers/slow-user"))
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.title").value("Upstream service timed out"))
				.andExpect(jsonPath("$.detail").value("GitHub API did not respond in time"));
	}

	@Test
	void returnsGatewayTimeoutWhenRepositoryResponseIsDelayed() throws Exception {
		mockMvc.perform(get("/developers/slow-user/repositories"))
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.title").value("Upstream service timed out"));
	}

	@Test
	void keepsNotFoundResponseForMissingGitHubUser() throws Exception {
		mockMvc.perform(get("/developers/missing-user"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Developer not found"));
	}

	@Test
	void returnsBadGatewayForNonTimeoutConnectionFailure() throws Exception {
		mockMvc.perform(get("/developers/disconnected-user"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.title").value("Upstream service unavailable"));
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
