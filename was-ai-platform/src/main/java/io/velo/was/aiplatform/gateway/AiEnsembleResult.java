package io.velo.was.aiplatform.gateway;

import java.util.List;

/**
 * Result of ensemble serving where multiple models process the same request
 * and their outputs are combined for improved accuracy and reliability.
 */
public record AiEnsembleResult(List<AiGatewayInferenceResult> candidates,
                               AiGatewayInferenceResult selected,
                               String combinationStrategy,
                               double ensembleConfidence,
                               int totalEstimatedTokens) {
}
