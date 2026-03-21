package io.velo.was.aiplatform.observability;

import io.velo.was.aiplatform.gateway.AiGatewayInferenceResult;
import io.velo.was.aiplatform.gateway.AiGatewayRequest;
import io.velo.was.aiplatform.gateway.AiGatewayRouteDecision;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlatformUsageServiceTest {

    @Test
    void snapshotsGatewayAndControlPlaneCounters() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiGatewayService gatewayService = new AiGatewayService(configuration, registryService);
        AiPlatformUsageService usageService = new AiPlatformUsageService();

        usageService.recordControlPlaneAccess("/api/overview");
        AiGatewayRouteDecision route = gatewayService.route(new AiGatewayRequest("CHAT", "Summarize the incident", "usage-route", false));
        usageService.recordRoute(route);
        AiGatewayInferenceResult inference = gatewayService.infer(new AiGatewayRequest("AUTO", "recommend products for a new user", "usage-infer", false));
        usageService.recordInference(inference, false);
        usageService.recordRegistryMutation("register");

        AiPlatformUsageSnapshot snapshot = usageService.snapshot(true, gatewayService, registryService);

        assertEquals(1, snapshot.controlPlaneCalls());
        assertEquals(1, snapshot.routeCalls());
        assertEquals(1, snapshot.inferCalls());
        assertEquals(1, snapshot.registryMutations());
        assertTrue(snapshot.totalEstimatedTokens() > 0);
        assertTrue(snapshot.modelRequestCounts().containsKey(route.modelName()));
        assertEquals(1L, snapshot.registryActionCounts().get("register"));
    }
}