package io.velo.was.aiplatform.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provider adapter for OpenAI API (GPT-4o, GPT-4o-mini, DALL-E, Whisper, TTS, etc.).
 * Also compatible with any OpenAI-format endpoint (vLLM, SGLang, LiteLLM).
 *
 * <p>Supports multimodal requests:
 * <ul>
 *   <li>Vision: image_url content type in messages (GPT-4o, vLLM vision models)</li>
 *   <li>Image generation: DALL-E 3, Stable Diffusion via OpenAI-compatible API</li>
 *   <li>Speech-to-text: Whisper transcription</li>
 *   <li>Text-to-speech: TTS synthesis</li>
 *   <li>Embeddings: text-embedding-3-small/large</li>
 * </ul>
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
    public List<String> supportedModalities() {
        return List.of("text", "vision", "image_gen", "stt", "tts", "embedding");
    }

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
        // Build messages — support vision (image_url) content
        String messagesJson = request.messages().stream()
                .map(this::messageToJson)
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
                    .timeout(Duration.ofSeconds(60))
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

    /**
     * Build a single message JSON — supports vision content (image_url).
     */
    private String messageToJson(AiProviderRequest.Message m) {
        if (m.hasImage()) {
            // OpenAI vision format: content is an array of parts
            StringBuilder parts = new StringBuilder();
            if (m.content() != null && !m.content().isBlank()) {
                parts.append("{\"type\":\"text\",\"text\":\"").append(escapeJson(m.content())).append("\"},");
            }
            parts.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"").append(escapeJson(m.imageUrl())).append("\"}}");
            return "{\"role\":\"" + escapeJson(m.role()) + "\",\"content\":[" + parts + "]}";
        }
        return "{\"role\":\"" + escapeJson(m.role()) + "\",\"content\":\"" + escapeJson(m.content()) + "\"}";
    }

    @Override
    public AiProviderResponse imageGeneration(AiProviderRequest request) throws AiProviderException {
        String body = "{\"model\":\"" + escapeJson(request.model()) + "\","
                + "\"prompt\":\"" + escapeJson(request.prompt()) + "\","
                + "\"n\":1,"
                + "\"response_format\":\"b64_json\","
                + "\"size\":\"1024x1024\"}";

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/images/generations"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException(id, response.statusCode(),
                        "Image generation error: HTTP " + response.statusCode());
            }

            // Parse: {"data":[{"b64_json":"...","revised_prompt":"..."}]}
            String b64 = extractJsonString(response.body(), "b64_json");
            String revisedPrompt = extractJsonString(response.body(), "revised_prompt");
            String content = revisedPrompt.isBlank() ? "Image generated successfully." : revisedPrompt;
            return new AiProviderResponse(id, request.model(), content, 0, 0, 0, "stop", latencyMs,
                    "image/png", b64.isBlank() ? null : b64);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException(id, 0, "Image generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiProviderResponse speechToText(AiProviderRequest request) throws AiProviderException {
        // Find audio data from messages
        String audioBase64 = request.messages().stream()
                .filter(AiProviderRequest.Message::hasAudio)
                .map(AiProviderRequest.Message::audioData)
                .findFirst()
                .orElse(null);

        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new AiProviderException(id, 400, "No audio data provided for speech-to-text");
        }

        // OpenAI whisper API requires multipart/form-data with file upload
        // For simplicity, use JSON-based transcription if endpoint supports it
        String body = "{\"model\":\"" + escapeJson(request.model()) + "\","
                + "\"file\":\"" + escapeJson(audioBase64) + "\","
                + "\"response_format\":\"json\"}";

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException(id, response.statusCode(),
                        "Speech-to-text error: HTTP " + response.statusCode());
            }

            String text = extractJsonString(response.body(), "text");
            int tokens = Math.max(8, text.length() / 4);
            return AiProviderResponse.of(id, request.model(), text, tokens, latencyMs);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException(id, 0, "Speech-to-text failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiProviderResponse textToSpeech(AiProviderRequest request) throws AiProviderException {
        String model = request.model().isBlank() ? "tts-1" : request.model();
        String body = "{\"model\":\"" + escapeJson(model) + "\","
                + "\"input\":\"" + escapeJson(request.prompt()) + "\","
                + "\"voice\":\"alloy\","
                + "\"response_format\":\"mp3\"}";

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/audio/speech"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException(id, response.statusCode(),
                        "Text-to-speech error: HTTP " + response.statusCode());
            }

            String base64Audio = Base64.getEncoder().encodeToString(response.body());
            return AiProviderResponse.binary(id, model, "audio/mp3", base64Audio, 0, latencyMs);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException(id, 0, "Text-to-speech failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiProviderResponse embeddings(AiProviderRequest request) throws AiProviderException {
        String model = request.model().isBlank() ? "text-embedding-3-small" : request.model();
        String body = "{\"model\":\"" + escapeJson(model) + "\","
                + "\"input\":\"" + escapeJson(request.prompt()) + "\"}";

        long startMs = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/embeddings"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() != 200) {
                throw new AiProviderException(id, response.statusCode(),
                        "Embedding error: HTTP " + response.statusCode());
            }

            int totalTokens = extractJsonInt(response.body(), "total_tokens", 0);
            // Return embedding as the content string (JSON array of floats)
            String embeddingData = extractJsonString(response.body(), "embedding");
            if (embeddingData.isBlank()) {
                embeddingData = response.body(); // fallback to raw response
            }
            return AiProviderResponse.of(id, model, embeddingData, totalTokens, latencyMs);
        } catch (AiProviderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AiProviderException(id, 0, "Embedding failed: " + e.getMessage(), e);
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
