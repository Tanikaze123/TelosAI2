package com.example.LoggerClient.util.APIHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class BackendLink {
	private static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");
	private static final Gson GSON = new Gson();

	// Server configuration
	private static final String BASE_URL = "http://127.0.0.1:4000";
	private static final String UPDATE_ENDPOINT = BASE_URL + "/api/update";
	private static final String ENTITIES_ENDPOINT = BASE_URL + "/api/entities";
	private static final String MOVEMENT_ENDPOINT = BASE_URL + "/api/movement";

	private final HttpClient httpClient;
	private boolean enabled = true;

	public BackendLink() {
		this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).build();

		// Test initial connection
		testConnection("Initialization");
	}

	// ========== CONNECTION TESTING ==========
	/**
	 * Test connection to backend server
	 * 
	 * @param message Message to send in test
	 */
	public void testConnection(String message) {
//		String json = "{\"message\":\"" + message + "\", \"sender\":\"Minecraft Mod\"}";
		JsonObject payload = new JsonObject();
		payload.addProperty("message", message);
		payload.addProperty("sender", "Minecraft Mod");
		payload.addProperty("timestamp", System.currentTimeMillis());

		sendAsync(UPDATE_ENDPOINT, payload, response -> {
			if (response != null && response.statusCode() == 200) {
				LOGGER.info("✓ Connected to backend at " + BASE_URL);
				enabled = true;
			} else {
				LOGGER.warn(
						"Backend connection failed - status: " + (response != null ? response.statusCode() : "null"));
				enabled = false;
			}
		}, error -> {
			LOGGER.warn("Could not connect to backend at " + BASE_URL);
			LOGGER.warn("Make sure server is running: npm start");
			enabled = false;
		});
	}

// ========== CORE HTTP METHODS ==========

	/**
	 * Send POST request asynchronously
	 * 
	 * @param url       Endpoint URL
	 * @param payload   JSON payload
	 * @param onSuccess Success callback
	 * @param onError   Error callback
	 */
	private void sendAsync(String url, JsonObject payload, ResponseCallback onSuccess, ErrorCallback onError) {

		CompletableFuture.runAsync(() -> {
			try {
				String json = GSON.toJson(payload);

				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(3))
						.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json))
						.build();

				httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
					if (onSuccess != null) {
						onSuccess.onResponse(response);
					}
				}).exceptionally(error -> {
					if (onError != null) {
						onError.onError(error);
					}
					return null;
				});

			} catch (Exception e) {
				if (onError != null) {
					onError.onError(e);
				}
			}
		});
	}

// ========== CALLBACKS ==========

	@FunctionalInterface
	public interface ResponseCallback {
		void onResponse(HttpResponse<String> response);
	}

	@FunctionalInterface
	public interface ErrorCallback {
		void onError(Throwable error);
	}
}
