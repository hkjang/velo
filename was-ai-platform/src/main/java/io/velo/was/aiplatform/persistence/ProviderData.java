package io.velo.was.aiplatform.persistence;

import java.util.List;
import java.util.Map;

/**
 * Provider 영속화용 DTO. data/ai-platform/providers.json 에 저장됩니다.
 */
public class ProviderData {
    private String providerId;
    private String displayName;
    private String type;           // "openai" (vLLM/SGLang), "anthropic", "ollama"
    private String baseUrl;
    private String apiKey;
    private List<String> models;
    private Map<String, String> customHeaders;
    private boolean enabled = true;
    private String createdAt;

    public ProviderData() {}

    public ProviderData(String providerId, String displayName, String type, String baseUrl, String apiKey,
                        List<String> models, Map<String, String> customHeaders, boolean enabled, String createdAt) {
        this.providerId = providerId; this.displayName = displayName; this.type = type;
        this.baseUrl = baseUrl; this.apiKey = apiKey; this.models = models;
        this.customHeaders = customHeaders; this.enabled = enabled; this.createdAt = createdAt;
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String v) { this.providerId = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { this.apiKey = v; }
    public List<String> getModels() { return models; }
    public void setModels(List<String> v) { this.models = v; }
    public Map<String, String> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(Map<String, String> v) { this.customHeaders = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }
}
