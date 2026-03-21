package io.velo.was.aiplatform.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Provider adapter for Anthropic API (Claude models).
 * Uses the Messages API format with x-api-key authentication.
 */
public class AnthropicProviderAdapter implements AiProviderAdapter {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public AnthropicProviderAdapter(String apiKey) {
        this(DEFAULT_BASE_URL, apiKey);
    }

    public AnthropicProviderAdapter(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String providerId() { return "anthropic"; }

    @Override
    public String displayName() { return "Anthropic"; }

    @Override
    public String protocol() { return "REST/SSE"; }

    @Override
    public boolean supportsStreaming() { return true; }

    @Override
    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Override
    public AiProviderResponse chatCompletion(AiProviderRequest request) throws AiProviderException {
        String messagesJson = request.messages().stream()
                .filter(m -> !"system".equals(m.role()))
                .map(m -> "{\"role\":\"" + escapeJson(m.role()) + "\",\"content\":\"" + escapeJson(m.content()) + "\"}")
                .collect(Collectors.joining(","));
        if (messagesJson.isEmpty()) {
            messagesJson = "{\"role\":\"user\",\"content\":\"" + escapeJson(request.prompt()) + "\"}";
        }

        String systemPrompt = request.messages().stream()
                .filter(m -> "system".equals(m.role()))
                .map(AiProviderRequest.Message::content)
                .findFirst().orElse("");

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(escapeJson(request.model())).append("\",");
        if (!systemPrompt.isEmpty()) {
            body.append("\"system\":\"").append(escapeJson(systemPrompt)).append("\",");
        }
        body.append("\"messages\":[").append(messagesJson).append("],");
        body.append("\"max_tokens\":").append(request.maxTokens()).append(",");
        body.append("\"temperature\":").append(request.temperature()).append("}");

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException("anthropic", response.statusCode(),
                        "Anthropic API error: HTTP " + response.statusCode());
            }

            return parseResponse(response.body(), latencyMs);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException("anthropic", 0, "Anthropic API connection failed: " + e.getMessage(), e);
        }
    }

    private AiProviderResponse parseResponse(String json, long latencyMs) {
        String content = extractTextContent(json);
        int inputTokens = extractJsonInt(json, "input_tokens", 0);
        int outputTokens = extractJsonInt(json, "output_tokens", 0);
        int totalTokens = inputTokens + outputTokens;
        if (totalTokens == 0) {
            totalTokens = Math.max(32, content.length() / 4);
            inputTokens = totalTokens / 3;
            outputTokens = totalTokens - inputTokens;
        }
        return new AiProviderResponse("anthropic", "claude", content, inputTokens, outputTokens, totalTokens, "stop", latencyMs);
    }

    private static String extractTextContent(String json) {
        int textIdx = json.indexOf("\"text\"");
        if (textIdx < 0) return "";
        int colonIdx = json.indexOf(':', textIdx + 6);
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
