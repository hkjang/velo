package io.velo.was.aiplatform.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provider adapter for Ollama (local/edge LLM inference).
 * Connects to Ollama's REST API at localhost:11434 by default.
 * Supports Llama, Mistral, Phi, Gemma, and other local models.
 */
public class OllamaProviderAdapter implements AiProviderAdapter {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final String baseUrl;
    private final HttpClient httpClient;

    public OllamaProviderAdapter() {
        this(DEFAULT_BASE_URL);
    }

    public OllamaProviderAdapter(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String providerId() { return "ollama"; }

    @Override
    public String displayName() { return "Ollama"; }

    @Override
    public String protocol() { return "REST"; }

    @Override
    public boolean supportsStreaming() { return true; }

    @Override
    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Override
    public AiProviderResponse chatCompletion(AiProviderRequest request) throws AiProviderException {
        String model = request.model().isBlank() ? "llama3.2" : request.model();
        String prompt = request.prompt();
        if (prompt.isBlank() && !request.messages().isEmpty()) {
            prompt = request.messages().get(request.messages().size() - 1).content();
        }

        String body = "{\"model\":\"" + escapeJson(model) + "\","
                + "\"prompt\":\"" + escapeJson(prompt) + "\","
                + "\"stream\":false}";

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException("ollama", response.statusCode(),
                        "Ollama API error: HTTP " + response.statusCode());
            }

            return parseResponse(response.body(), model, latencyMs);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException("ollama", 0, "Ollama connection failed: " + e.getMessage(), e);
        }
    }

    private AiProviderResponse parseResponse(String json, String model, long latencyMs) {
        String content = extractJsonString(json, "response");
        int totalTokens = Math.max(32, content.length() / 4);
        return AiProviderResponse.of("ollama", model, content, totalTokens, latencyMs);
    }

    private static String extractJsonString(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "";
        int colonIdx = json.indexOf(':', idx + field.length() + 2);
        if (colonIdx < 0) return "";
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return "";
        int quoteEnd = findClosingQuote(json, quoteStart + 1);
        if (quoteEnd < 0) return "";
        return json.substring(quoteStart + 1, quoteEnd)
                .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int findClosingQuote(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            if (json.charAt(i) == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
