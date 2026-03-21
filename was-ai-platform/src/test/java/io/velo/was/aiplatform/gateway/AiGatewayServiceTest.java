package io.velo.was.aiplatform.gateway;

import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGatewayServiceTest {

    @Test
    void promptRoutingChoosesVisionModel() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiGatewayService service = new AiGatewayService(configuration);
        AiGatewayRouteDecision decision = service.route(new AiGatewayRequest("", "Analyze this screenshot and OCR the image", "vision-session", false));

        assertEquals("VISION", decision.resolvedType());
        assertEquals("vision-edge", decision.modelName());
        assertTrue(decision.promptRouted());
    }

    @Test
    void latencyFirstAutoSelectionPrefersFastestCandidateInCategory() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getServing().getModels().add(
                new ServerConfiguration.ModelProfile("llm-fast", "LLM", "builtin", "v2", "ultra-low", 120, 74, false, true)
        );
        configuration.getServer().getAiPlatform().getServing().setDefaultStrategy("LATENCY_FIRST");
        configuration.getServer().getAiPlatform().getServing().setAbTestingEnabled(false);
        configuration.validate();

        AiGatewayService service = new AiGatewayService(configuration);
        AiGatewayRouteDecision decision = service.route(new AiGatewayRequest("CHAT", "Summarize the incident report", "chat-session", false));

        assertEquals("CHAT", decision.resolvedType());
        assertEquals("llm-fast", decision.modelName());
        assertEquals("LATENCY_FIRST", decision.strategyApplied());
    }

    @Test
    void repeatedSessionPromptUsesContextCache() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiGatewayService service = new AiGatewayService(configuration);
        AiGatewayInferenceResult first = service.infer(new AiGatewayRequest("CHAT", "hello from cache", "cache-session", false));
        AiGatewayInferenceResult second = service.infer(new AiGatewayRequest("CHAT", "hello from cache", "cache-session", false));

        assertFalse(first.decision().cacheHit());
        assertTrue(second.decision().cacheHit());
        assertEquals(first.decision().modelName(), second.decision().modelName());
    }
}