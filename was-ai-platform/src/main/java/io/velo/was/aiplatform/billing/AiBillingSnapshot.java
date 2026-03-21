package io.velo.was.aiplatform.billing;

import java.util.List;

public record AiBillingSnapshot(
        boolean billingEnabled,
        String currency,
        long meteredRequests,
        long totalEstimatedTokens,
        double estimatedTotalCost,
        double averageCostPerRequest,
        List<AiBillingLineItem> lineItems
) {
}