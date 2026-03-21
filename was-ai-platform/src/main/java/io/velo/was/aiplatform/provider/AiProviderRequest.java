package io.velo.was.aiplatform.provider;

import java.util.List;

/**
 * Unified request format sent to AI provider adapters.
 */
public record AiProviderRequest(String model,
                                List<Message> messages,
                                String prompt,
                                double temperature,
                                int maxTokens,
                                boolean stream) {

    public record Message(String role, String content) {
    }

    public static AiProviderRequest chat(String model, String userMessage) {
        return new AiProviderRequest(model,
                List.of(new Message("user", userMessage)),
                userMessage, 0.7d, 1024, false);
    }

    public static AiProviderRequest completion(String model, String prompt) {
        return new AiProviderRequest(model, List.of(), prompt, 0.7d, 1024, false);
    }
}
