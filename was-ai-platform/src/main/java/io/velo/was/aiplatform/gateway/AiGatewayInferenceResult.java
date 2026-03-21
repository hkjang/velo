package io.velo.was.aiplatform.gateway;

public record AiGatewayInferenceResult(AiGatewayRouteDecision decision,
                                       String outputText,
                                       int estimatedTokens,
                                       double confidence) {
}