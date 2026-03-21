package io.velo.was.aiplatform.billing;

import io.velo.was.aiplatform.observability.AiPlatformUsageSnapshot;
import io.velo.was.aiplatform.publishing.AiPublishedApiService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AiBillingService {

    private final AiPublishedApiService publishedApiService;
    private final AiModelRegistryService registryService;

    public AiBillingService(AiPublishedApiService publishedApiService, AiModelRegistryService registryService) {
        this.publishedApiService = publishedApiService;
        this.registryService = registryService;
    }

    public AiBillingSnapshot snapshot(AiPlatformUsageSnapshot usage) {
        long meteredRequests = usage.meteredRequests();
        long totalTokens = usage.totalEstimatedTokens();
        List<AiBillingLineItem> lineItems = new ArrayList<>();
        for (Map.Entry<String, Long> entry : usage.modelRequestCounts().entrySet()) {
            ServerConfiguration.ModelProfile model = registryService.routingModels().stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (model == null) {
                continue;
            }
            long allocatedTokens = meteredRequests == 0 ? 0 : Math.round((double) totalTokens * ((double) entry.getValue() / (double) meteredRequests));
            double cost = entry.getValue() * publishedApiService.estimateCost(model, allocatedTokens == 0 ? 32 : (int) allocatedTokens);
            lineItems.add(new AiBillingLineItem(model.getName(), model.getCategory(), entry.getValue(), allocatedTokens, cost));
        }
        lineItems.sort(Comparator.comparing(AiBillingLineItem::modelName));
        double totalCost = lineItems.stream().mapToDouble(AiBillingLineItem::estimatedCost).sum();
        double average = meteredRequests == 0 ? 0.0d : totalCost / meteredRequests;
        return new AiBillingSnapshot(usage.billingEnabled(), "USD", meteredRequests, totalTokens, totalCost, average, List.copyOf(lineItems));
    }
}