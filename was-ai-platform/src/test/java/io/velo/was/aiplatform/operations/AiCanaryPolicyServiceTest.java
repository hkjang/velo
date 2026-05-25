package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.audit.AiGatewayAuditLog;
import io.velo.was.aiplatform.registry.AiModelRegistrationRequest;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiCanaryPolicyServiceTest {

    @Test
    void promotesHealthyCanaryWhenApplyIsTrue() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getAdvanced().setCanaryPromotionMinRequests(2);
        configuration.validate();
        AiModelRegistryService registry = new AiModelRegistryService(configuration);
        registry.registerOrUpdate(new AiModelRegistrationRequest(
                "llm-general", "LLM", "builtin", "v2", "balanced", 200, 91, false, true, "CANARY", "runtime"
        ));
        AiGatewayAuditLog auditLog = new AiGatewayAuditLog();
        auditLog.recordSuccess("tenant", "gateway/infer", "llm-general", "builtin", "CHAT",
                "hello", 100, 30, false, "127.0.0.1", "canary", null, "text");
        auditLog.recordSuccess("tenant", "gateway/infer", "llm-general", "builtin", "CHAT",
                "hello again", 120, 30, false, "127.0.0.1", "canary", null, "text");

        AiCanaryPolicyService service = new AiCanaryPolicyService(configuration, registry, auditLog);

        assertEquals("PROMOTE", service.evaluate(true).get(0).action());
        assertEquals("v2", registry.findModel("llm-general").activeVersion());
    }
}
