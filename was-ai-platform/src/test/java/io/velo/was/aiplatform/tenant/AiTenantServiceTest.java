package io.velo.was.aiplatform.tenant;

import io.velo.was.aiplatform.persistence.AiPlatformDataStore;
import io.velo.was.aiplatform.persistence.TenantData;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTenantServiceTest {

    @TempDir
    Path tempDir;

    private static ServerConfiguration multiTenantConfig() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getPlatform().setMultiTenantEnabled(true);
        configuration.validate();
        return configuration;
    }

    @Test
    void bootstrapsDemoTenantWithoutPublicApiKey() {
        AiTenantService service = new AiTenantService(multiTenantConfig());

        AiTenantSnapshot snapshot = service.snapshot();
        assertTrue(snapshot.totalTenants() >= 1);
        assertTrue(snapshot.tenants().stream()
                .filter(tenant -> "tenant-demo".equals(tenant.tenantId()))
                .findFirst()
                .orElseThrow()
                .apiKeys()
                .isEmpty());
        assertThrows(SecurityException.class, () -> service.authorize("velo-demo-key"));
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

    @Test
    void revokesIssuedApiKey() {
        AiTenantService service = new AiTenantService(multiTenantConfig());
        service.registerOrUpdate(new AiTenantRegistrationRequest("tenant-revoke", "Revoke", "starter", 10, 500L, true));
        AiTenantIssuedKey issuedKey = service.issueApiKey("tenant-revoke", "temporary");

        assertNotNull(service.authorize(issuedKey.apiKey()));

        service.revokeApiKey("tenant-revoke", issuedKey.keyId());

        assertThrows(SecurityException.class, () -> service.authorize(issuedKey.apiKey()));
        assertFalse(service.getTenant("tenant-revoke").apiKeys().stream()
                .filter(key -> issuedKey.keyId().equals(key.keyId()))
                .findFirst()
                .orElseThrow()
                .active());
    }

    @Test
    void persistsUsageAcrossRestart() {
        ServerConfiguration configuration = multiTenantConfig();
        AiPlatformDataStore dataStore = new AiPlatformDataStore(tempDir);
        AiTenantService service = new AiTenantService(configuration, dataStore);
        service.registerOrUpdate(new AiTenantRegistrationRequest("persist-tenant", "Persist", "pro", 20, 1000L, true));
        AiTenantIssuedKey key = service.issueApiKey("persist-tenant", "persist-key");

        AiTenantAccessGrant grant = service.authorize(key.apiKey());
        service.recordUsage(grant, 42);

        AiTenantService restarted = new AiTenantService(configuration, dataStore);
        AiTenantUsageInfo usage = restarted.getTenantUsage("persist-tenant");
        assertEquals(1, usage.totalRequests());
        assertEquals(42, usage.totalTokens());
    }

    @Test
    void ignoresLegacyDemoApiKeyFromDisk() {
        ServerConfiguration configuration = multiTenantConfig();
        AiPlatformDataStore dataStore = new AiPlatformDataStore(tempDir);
        dataStore.save("tenants.json", List.of(
                new TenantData("tenant-demo", "Demo Tenant", "starter", true, 120, 250000L, 1000L,
                        List.of(new TenantData.ApiKeyData("bootstrap", "bootstrap", "velo-demo-key", true, 1000L)))
        ));

        AiTenantService service = new AiTenantService(configuration, dataStore);

        assertTrue(service.getTenant("tenant-demo").apiKeys().isEmpty());
        assertThrows(SecurityException.class, () -> service.authorize("velo-demo-key"));
    }
}
