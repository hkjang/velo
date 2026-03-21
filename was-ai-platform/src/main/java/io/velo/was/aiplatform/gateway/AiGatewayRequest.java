package io.velo.was.aiplatform.gateway;

public record AiGatewayRequest(String requestType,
                               String prompt,
                               String sessionId,
                               boolean streamingRequested) {
}