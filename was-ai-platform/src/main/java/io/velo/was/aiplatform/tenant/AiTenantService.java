package io.velo.was.aiplatform.tenant;

import io.velo.was.config.ServerConfiguration;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class AiTenantService {

    private static final String DEMO_TENANT_ID = "tenant-demo";
    private static final String DEMO_API_KEY = "velo-demo-key";

    private final ServerConfiguration configuration;
    private final ConcurrentMap<String, MutableTenant> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ApiKeyRef> apiKeyIndex = new ConcurrentHashMap<>();
    private final AtomicLong issuedKeySequence = new AtomicLong();
    private final SecureRandom secureRandom = new SecureRandom();

    public AiTenantService(ServerConfiguration configuration) {
        this.configuration = configuration;
        bootstrapDemoTenant();
    }

    public boolean isMultiTenantEnabled() {
        return configuration.getServer().getAiPlatform().getPlatform().isMultiTenantEnabled();
    }

    public String apiKeyHeader() {
        return configuration.getServer().getAiPlatform().getPlatform().getApiKeyHeader();
    }

    public synchronized AiTenantSnapshot snapshot() {
        List<AiTenantProfile> tenantProfiles = listTenants();
        int activeTenants = (int) tenantProfiles.stream().filter(AiTenantProfile::active).count();
        return new AiTenantSnapshot(
                isMultiTenantEnabled(),
                apiKeyHeader(),
                tenantProfiles.size(),
                activeTenants,
                isMultiTenantEnabled() ? DEMO_API_KEY : "",
                tenantProfiles
        );
    }

    public synchronized List<AiTenantProfile> listTenants() {
        return tenants.values().stream()
                .map(this::snapshotTenant)
                .sorted(Comparator.comparing(AiTenantProfile::tenantId))
                .toList();
    }

    public synchronized AiTenantProfile getTenant(String tenantId) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        return snapshotTenant(tenant);
    }

    public synchronized AiTenantUsageInfo getTenantUsage(String tenantId) {
        return getTenant(tenantId).usage();
    }

    public synchronized AiTenantProfile registerOrUpdate(AiTenantRegistrationRequest request) {
        String tenantId = normalizeTenantId(request.tenantId());
        String displayName = request.displayName() == null || request.displayName().isBlank() ? tenantId : request.displayName().trim();
        String plan = request.plan() == null || request.plan().isBlank() ? "starter" : request.plan().trim().toLowerCase(Locale.ROOT);
        int rateLimit = request.rateLimitPerMinute() > 0
                ? request.rateLimitPerMinute()
                : configuration.getServer().getAiPlatform().getPlatform().getDefaultTenantRateLimitPerMinute();
        long tokenQuota = request.tokenQuota() > 0
                ? request.tokenQuota()
                : configuration.getServer().getAiPlatform().getPlatform().getDefaultTenantTokenQuota();
        long now = System.currentTimeMillis();
        MutableTenant tenant = tenants.computeIfAbsent(normalizeKey(tenantId), ignored -> new MutableTenant(tenantId, now));
        tenant.tenantId = tenantId;
        tenant.displayName = displayName;
        tenant.plan = plan;
        tenant.active = request.active();
        tenant.rateLimitPerMinute = rateLimit;
        tenant.tokenQuota = tokenQuota;
        return snapshotTenant(tenant);
    }

    public synchronized void removeTenant(String tenantId) {
        MutableTenant removed = tenants.remove(normalizeKey(tenantId));
        if (removed == null) {
            throw new NoSuchElementException("테넌트를 찾을 수 없습니다: " + tenantId);
        }
        // Clean up API key index entries for this tenant
        for (MutableApiKey key : removed.apiKeys.values()) {
            apiKeyIndex.remove(key.secret);
        }
    }

    public synchronized AiTenantIssuedKey issueApiKey(String tenantId, String label) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        String normalizedLabel = label == null || label.isBlank() ? "default" : label.trim();
        long now = System.currentTimeMillis();
        String keyId = "key-" + Long.toString(issuedKeySequence.incrementAndGet(), 36);
        byte[] randomBytes = new byte[6];
        secureRandom.nextBytes(randomBytes);
        String secret = "vtk_" + normalizeKey(tenant.tenantId) + "_" + HexFormat.of().formatHex(randomBytes);
        MutableApiKey apiKey = new MutableApiKey(keyId, normalizedLabel, secret, now);
        tenant.apiKeys.put(normalizeKey(keyId), apiKey);
        apiKeyIndex.put(secret, new ApiKeyRef(normalizeKey(tenant.tenantId), normalizeKey(keyId)));
        return new AiTenantIssuedKey(tenant.tenantId, tenant.displayName, tenant.plan, keyId, normalizedLabel, secret, now);
    }

    public synchronized AiTenantAccessGrant authorize(String apiKey) {
        if (!isMultiTenantEnabled()) {
            return new AiTenantAccessGrant("public", "Public Access", "shared", "shared", 0, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Long.MAX_VALUE, 0L, Long.MAX_VALUE, false);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new SecurityException("API key is required. Send " + apiKeyHeader() + " or Authorization: Bearer <token>.");
        }
        ApiKeyRef ref = apiKeyIndex.get(apiKey.trim());
        if (ref == null) {
            throw new SecurityException("Unknown API key");
        }
        MutableTenant tenant = tenants.get(ref.tenantKey());
        if (tenant == null || !tenant.active) {
            throw new SecurityException("Tenant is not active");
        }
        MutableApiKey key = tenant.apiKeys.get(ref.keyKey());
        if (key == null || !key.active) {
            throw new SecurityException("API key is inactive");
        }
        long now = System.currentTimeMillis();
        refreshWindow(tenant, now);
        int nextWindowRequests = tenant.currentWindowRequests + 1;
        if (nextWindowRequests > tenant.rateLimitPerMinute) {
            throw new IllegalStateException("Rate limit exceeded for tenant " + tenant.tenantId + ". Retry after the current minute window resets.");
        }
        long remainingTokens = Math.max(0L, tenant.tokenQuota - tenant.totalTokens);
        if (remainingTokens <= 0L) {
            throw new IllegalStateException("Token quota exhausted for tenant " + tenant.tenantId);
        }
        return new AiTenantAccessGrant(
                tenant.tenantId,
                tenant.displayName,
                tenant.plan,
                key.keyId,
                nextWindowRequests,
                tenant.rateLimitPerMinute,
                Math.max(0, tenant.rateLimitPerMinute - nextWindowRequests),
                tenant.tokenQuota,
                tenant.totalTokens,
                remainingTokens,
                true
        );
    }

    public synchronized void recordUsage(AiTenantAccessGrant grant, int estimatedTokens) {
        if (grant == null || !grant.tracked()) {
            return;
        }
        MutableTenant tenant = tenants.get(normalizeKey(grant.tenantId()));
        if (tenant == null) {
            return;
        }
        long now = System.currentTimeMillis();
        refreshWindow(tenant, now);
        tenant.currentWindowRequests = Math.max(tenant.currentWindowRequests + 1, grant.currentWindowRequests());
        tenant.totalRequests++;
        tenant.totalTokens += Math.max(0, estimatedTokens);
        tenant.lastActivityEpochMillis = now;
        MutableApiKey apiKey = tenant.apiKeys.get(normalizeKey(grant.keyId()));
        if (apiKey != null) {
            apiKey.lastUsedAtEpochMillis = now;
        }
    }

    private void bootstrapDemoTenant() {
        registerOrUpdate(new AiTenantRegistrationRequest(
                DEMO_TENANT_ID,
                "Demo Tenant",
                "starter",
                configuration.getServer().getAiPlatform().getPlatform().getDefaultTenantRateLimitPerMinute(),
                configuration.getServer().getAiPlatform().getPlatform().getDefaultTenantTokenQuota(),
                true
        ));
        seedApiKey(DEMO_TENANT_ID, "bootstrap", DEMO_API_KEY);
    }

    private void seedApiKey(String tenantId, String label, String secret) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null || apiKeyIndex.containsKey(secret)) {
            return;
        }
        long now = System.currentTimeMillis();
        MutableApiKey apiKey = new MutableApiKey("bootstrap", label, secret, now);
        tenant.apiKeys.put(normalizeKey(apiKey.keyId), apiKey);
        apiKeyIndex.put(secret, new ApiKeyRef(normalizeKey(tenant.tenantId), normalizeKey(apiKey.keyId)));
    }

    private AiTenantProfile snapshotTenant(MutableTenant tenant) {
        refreshWindow(tenant, System.currentTimeMillis());
        List<AiTenantApiKeyInfo> apiKeys = tenant.apiKeys.values().stream()
                .sorted(Comparator.comparing(key -> key.keyId))
                .map(key -> new AiTenantApiKeyInfo(
                        key.keyId,
                        key.label,
                        maskSecret(key.secret),
                        key.active,
                        key.createdAtEpochMillis,
                        key.lastUsedAtEpochMillis
                ))
                .toList();
        AiTenantUsageInfo usage = new AiTenantUsageInfo(
                tenant.totalRequests,
                tenant.totalTokens,
                tenant.currentWindowRequests,
                tenant.rateLimitPerMinute,
                Math.max(0, tenant.rateLimitPerMinute - tenant.currentWindowRequests),
                tenant.tokenQuota,
                Math.max(0L, tenant.tokenQuota - tenant.totalTokens),
                tenant.lastActivityEpochMillis
        );
        return new AiTenantProfile(
                tenant.tenantId,
                tenant.displayName,
                tenant.plan,
                tenant.active,
                tenant.rateLimitPerMinute,
                tenant.tokenQuota,
                tenant.createdAtEpochMillis,
                apiKeys,
                usage
        );
    }

    private static void refreshWindow(MutableTenant tenant, long now) {
        long windowStart = now / 60_000L;
        if (tenant.currentWindowEpochMinute != windowStart) {
            tenant.currentWindowEpochMinute = windowStart;
            tenant.currentWindowRequests = 0;
        }
    }

    private static String normalizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        if (secret.length() <= 8) {
            return secret.charAt(0) + "***";
        }
        return secret.substring(0, 6) + "..." + secret.substring(secret.length() - 4);
    }

    private record ApiKeyRef(String tenantKey, String keyKey) {
    }

    private static final class MutableTenant {
        private String tenantId;
        private String displayName = "Tenant";
        private String plan = "starter";
        private boolean active = true;
        private int rateLimitPerMinute = 120;
        private long tokenQuota = 250_000L;
        private final long createdAtEpochMillis;
        private final ConcurrentMap<String, MutableApiKey> apiKeys = new ConcurrentHashMap<>();
        private long totalRequests;
        private long totalTokens;
        private long currentWindowEpochMinute;
        private int currentWindowRequests;
        private long lastActivityEpochMillis;

        private MutableTenant(String tenantId, long createdAtEpochMillis) {
            this.tenantId = tenantId;
            this.displayName = tenantId;
            this.createdAtEpochMillis = createdAtEpochMillis;
        }
    }

    private static final class MutableApiKey {
        private final String keyId;
        private final String label;
        private final String secret;
        private boolean active = true;
        private final long createdAtEpochMillis;
        private long lastUsedAtEpochMillis;

        private MutableApiKey(String keyId, String label, String secret, long createdAtEpochMillis) {
            this.keyId = keyId;
            this.label = label;
            this.secret = secret;
            this.createdAtEpochMillis = createdAtEpochMillis;
        }
    }
}