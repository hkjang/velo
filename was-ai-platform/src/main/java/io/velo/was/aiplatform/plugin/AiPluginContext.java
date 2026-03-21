package io.velo.was.aiplatform.plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable context that flows through the plugin pipeline. Holds the prompt,
 * request type, output text, and arbitrary metadata attributes.
 */
public final class AiPluginContext {

    private String prompt;
    private String requestType;
    private String sessionId;
    private String outputText;
    private String modelName;
    private int estimatedTokens;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public AiPluginContext(String prompt, String requestType, String sessionId) {
        this.prompt = prompt;
        this.requestType = requestType;
        this.sessionId = sessionId;
        this.outputText = "";
        this.modelName = "";
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getOutputText() { return outputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public int getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(int estimatedTokens) { this.estimatedTokens = estimatedTokens; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }
}
