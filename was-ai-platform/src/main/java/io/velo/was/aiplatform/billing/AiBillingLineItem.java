package io.velo.was.aiplatform.billing;

public record AiBillingLineItem(
        String modelName,
        String category,
        long requests,
        long allocatedTokens,
        double estimatedCost
) {
}