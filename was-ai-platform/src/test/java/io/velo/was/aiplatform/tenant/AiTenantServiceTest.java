package io.velo.was.aiplatform.tenant;

import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTenantServiceTest {

    private static ServerConfiguration multiTenantConfig() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getPlatform().setMultiTenantEnabled(true);
        configuration.validate();
        return configuration;
    }

    @Test
    void bootstrapsDemoTenant() {
        AiTenantService service = new AiTenantService(multiTenantConfig());

        AiTenantSnapshot snapshot = service.snapshot();
        assertTrue(snapshot.totalTenants() >= 1);
        assertEquals("velo-demo-key", snapshot.bootstrapApiKey());
    }

    @Test
    void registerAndRetrieveTenant() {
        AiTenantService service = new AiTenantService(multiTenantConfig());

        service.registerOrUpdate(new AiTenantRegistrationRequest("test-tenant", "Test", "pro", 200, 500000L, true));
        AiTenantProfile profile = service.getTenant("test-tenant");

        assertEquals("test-tenant", profile.tenantId());
        assertEquals("Test", profile.displayName());
        assertEquals("pro", profile.plan());
        assertEquals(200, profile.rateLimitPerMinute());
        assertTrue(profile.active());
    }

    @Test
    void issueApiKeyAndAuthorize() {
        AiTenantService service = new AiTenantService(multiTenantConfig());

        service.registerOrUpdate(new AiTenantRegistrationRequest("key-tenant", "Key Tenant", "starter", 60, 100000L, true));
        AiTenantIssuedKey key = service.issueApiKey("key-tenant", "test-key");

        assertNotNull(key.apiKey());
        assertTrue(key.apiKey().startsWith("vtk_"));

        AiTenantAccessGrant grant = service.authorize(key.apiKey());
        assertNotNull(grant);
        assertEquals("key-tenant", grant.tenantId());
        assertTrue(grant.tracked());
    }

    @Test
    void rateLimitEnforced() {
        AiTenantService service = new AiTenantService(multiTenantConfig());

        service.registerOrUpdate(new AiTenantRegistrationRequest("rate-tenant", "Rate", "starter", 2, 100000L, true));
        AiTenantIssuedKey key = service.issueApiKey("rate-tenant", "rate-key");

        // authorize + recordUsage to actually increment the window counter
        AiTenantAccessGrant grant1 = service.authorize(key.apiKey());
        service.recordUsage(grant1, 10);
        AiTenantAccessGrant grant2 = service.authorize(key.apiKey());
        service.recordUsage(grant2, 10);
        // Third call should exceed rate limit of 2/min
        assertThrows(IllegalStateException.class, () -> service.authorize(key.apiKey()));
    }

    @Test
    void unknownApiKeyThrowsSecurity() {
        AiTenantService service = new AiTenantService(multiTenantConfig());
        assertThrows(SecurityException.class, () -> service.authorize("invalid-key"));
    }
}
