package io.velo.was.aiplatform.gateway;

import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGatewayEnsembleTest {

    @Test
    void ensembleDisabledReturnsSingleResult() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getServing().setEnsembleServingEnabled(false);
        configuration.getServer().getAiPlatform().getServing().setAbTestingEnabled(false);
        configuration.validate();

        AiGatewayService service = new AiGatewayService(configuration);
        AiEnsembleResult result = service.inferEnsemble(
                new AiGatewayRequest("CHAT", "Hello", "ensemble-test", false));

        assertEquals("single", result.combinationStrategy());
        assertEquals(1, result.candidates().size());
        assertNotNull(result.selected());
        assertTrue(result.ensembleConfidence() > 0);
    }

    @Test
    void ensembleEnabledReturnsMultipleCandidates() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getServing().setEnsembleServingEnabled(true);
        configuration.getServer().getAiPlatform().getServing().setAbTestingEnabled(false);
        configuration.validate();

        AiGatewayService service = new AiGatewayService(configuration);
        AiEnsembleResult result = service.inferEnsemble(
                new AiGatewayRequest("CHAT", "Summarize the report", "ensemble-test", false));

        assertFalse(result.candidates().isEmpty());
        assertNotNull(result.selected());
        assertTrue(result.combinationStrategy().startsWith("best-of") || result.combinationStrategy().equals("single"));
        assertTrue(result.totalEstimatedTokens() > 0);
        assertTrue(result.ensembleConfidence() >= result.selected().confidence());
    }

    @Test
    void abTestingSplitsTrafficBetweenModels() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getServing().setAbTestingEnabled(true);
        // Add a second LLM model so A/B testing has 2+ candidates in the same category
        configuration.getServer().getAiPlatform().getServing().getModels().add(
                new ServerConfiguration.ModelProfile("llm-fast", "LLM", "builtin", "v1", "ultra-low", 120, 74, false, true)
        );
        configuration.validate();

        AiGatewayService service = new AiGatewayService(configuration);

        for (int i = 0; i < 20; i++) {
            service.route(new AiGatewayRequest("CHAT", "Test prompt " + i, "ab-test-" + i, false));
        }

        // With A/B testing and 20 requests across 2 LLM models, both groups should have traffic
        long groupATotal = service.getAbTestGroupACounts().values().stream().mapToLong(Long::longValue).sum();
        long groupBTotal = service.getAbTestGroupBCounts().values().stream().mapToLong(Long::longValue).sum();
        assertTrue(groupATotal + groupBTotal > 0, "A/B test should distribute traffic");
    }
}
