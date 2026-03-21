package io.velo.was.aiplatform.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Provider adapter for OpenAI API (GPT-4o, GPT-4o-mini, etc.).
 * Also compatible with any OpenAI-format endpoint (vLLM, SGLang, LiteLLM).
 */
public class OpenAiProviderAdapter implements AiProviderAdapter {

    private final String id;
    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public OpenAiProviderAdapter(String baseUrl, String apiKey) {
        this("openai", "OpenAI", baseUrl, apiKey);
    }

    public OpenAiProviderAdapter(String id, String name, String baseUrl, String apiKey) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String providerId() { return id; }

    @Override
    public String displayName() { return name; }

    @Override
    public String protocol() { return "REST/SSE"; }

    @Override
    public boolean supportsStreaming() { return true; }

    @Override
    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(5))
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
        String messagesJson = request.messages().stream()
                .map(m -> "{\"role\":\"" + escapeJson(m.role()) + "\",\"content\":\"" + escapeJson(m.content()) + "\"}")
                .collect(Collectors.joining(","));
        if (messagesJson.isEmpty()) {
            messagesJson = "{\"role\":\"user\",\"content\":\"" + escapeJson(request.prompt()) + "\"}";
        }

        String body = "{\"model\":\"" + escapeJson(request.model()) + "\","
                + "\"messages\":[" + messagesJson + "],"
                + "\"temperature\":" + request.temperature() + ","
                + "\"max_tokens\":" + request.maxTokens() + "}";

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException(id, response.statusCode(),
                        "OpenAI API error: HTTP " + response.statusCode());
            }

            return parseResponse(response.body(), latencyMs);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException(id, 0, "OpenAI API connection failed: " + e.getMessage(), e);
        }
    }

    private AiProviderResponse parseResponse(String json, long latencyMs) {
        String content = extractJsonString(json, "content");
        int totalTokens = extractJsonInt(json, "total_tokens", 0);
        int promptTokens = extractJsonInt(json, "prompt_tokens", 0);
        int completionTokens = extractJsonInt(json, "completion_tokens", 0);
        if (totalTokens == 0) {
            totalTokens = Math.max(32, content.length() / 4);
            promptTokens = totalTokens / 3;
            completionTokens = totalTokens - promptTokens;
        }
        return new AiProviderResponse(id, "openai", content, promptTokens, completionTokens, totalTokens, "stop", latencyMs);
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

    private static int extractJsonInt(String json, String field, int fallback) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return fallback;
        int colonIdx = json.indexOf(':', idx + field.length() + 2);
        if (colonIdx < 0) return fallback;
        StringBuilder num = new StringBuilder();
        for (int i = colonIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c) || c == '-') num.append(c);
            else if (!num.isEmpty()) break;
        }
        try {
            return num.isEmpty() ? fallback : Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
