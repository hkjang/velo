package io.velo.was.aiplatform.publishing;

import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public class AiPublishedApiService {

    private final ServerConfiguration configuration;
    private final AiModelRegistryService registryService;

    public AiPublishedApiService(ServerConfiguration configuration, AiModelRegistryService registryService) {
        this.configuration = configuration;
        this.registryService = registryService;
    }

    public boolean isEnabled() {
        return configuration.getServer().getAiPlatform().getPlatform().isAutoApiGenerationEnabled();
    }

    public List<AiPublishedEndpoint> listEndpoints(String contextPath) {
        if (!isEnabled()) {
            return List.of();
        }
        return registryService.routingModels().stream()
                .sorted(Comparator.comparing(ServerConfiguration.ModelProfile::getName))
                .map(model -> new AiPublishedEndpoint(
                        model.getName() + "-invoke",
                        model.getName(),
                        model.getVersion(),
                        model.getCategory(),
                        "POST",
                        contextPath + "/invoke/" + model.getName(),
                        true,
                        "Dedicated generated endpoint for the active " + model.getName() + " model profile"
                ))
                .toList();
    }

    public AiPublishedInvocationResult invoke(String contextPath, String modelName, String prompt, String sessionId) {
        if (!isEnabled()) {
            throw new IllegalStateException("Auto API generation is disabled in configuration");
        }
        ServerConfiguration.ModelProfile model = registryService.routingModels().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(modelName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Published model endpoint not found: " + modelName));
        AiPublishedEndpoint endpoint = new AiPublishedEndpoint(
                model.getName() + "-invoke",
                model.getName(),
                model.getVersion(),
                model.getCategory(),
                "POST",
                contextPath + "/invoke/" + model.getName(),
                true,
                "Dedicated generated endpoint for the active " + model.getName() + " model profile"
        );
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        String outputText = switch (model.getCategory()) {
            case "CV" -> "Generated vision API " + model.getName() + " processed the request. Prompt preview: " + preview(normalizedPrompt)
                    + " Response adapters can now be bound behind this dedicated endpoint.";
            case "RECOMMENDER" -> "Generated recommendation API " + model.getName() + " returned a personalization-ready response. Prompt preview: " + preview(normalizedPrompt)
                    + " This endpoint is pinned to the currently active registered model version.";
            default -> "Generated language API " + model.getName() + " returned a standalone model response. Prompt preview: " + preview(normalizedPrompt)
                    + " This endpoint is pinned to the currently active registered model version.";
        };
        int estimatedTokens = Math.max(32, outputText.length() / 4);
        double estimatedCost = estimateCost(model, estimatedTokens);
        return new AiPublishedInvocationResult(endpoint, sessionId == null ? "" : sessionId.trim(), outputText,
                estimatedTokens, estimatedCost, model.getProvider(), model.getLatencyMs());
    }

    public double estimateCost(ServerConfiguration.ModelProfile model, int tokens) {
        double requestRate = switch (model.getCategory()) {
            case "CV" -> 0.0022d;
            case "RECOMMENDER" -> 0.0014d;
            default -> 0.0031d;
        };
        double tokenRate = switch (model.getCategory()) {
            case "CV" -> 0.00035d;
            case "RECOMMENDER" -> 0.00018d;
            default -> 0.0011d;
        };
        return requestRate + ((tokens / 1000.0d) * tokenRate);
    }

    private static String preview(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "No prompt provided.";
        }
        return prompt.length() <= 96 ? prompt : prompt.substring(0, 93) + "...";
    }
}