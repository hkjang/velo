package io.velo.was.aiplatform.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import io.velo.was.aiplatform.persistence.AiPlatformDataStore;
import io.velo.was.aiplatform.persistence.TenantData;
import io.velo.was.config.ServerConfiguration;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class AiTenantService {

    private static final String DEMO_TENANT_ID = "tenant-demo";
    private static final String LEGACY_DEMO_API_KEY = "velo-demo-key";
    private static final String TENANTS_FILE = "tenants.json";

    private final ServerConfiguration configuration;
    private volatile AiPlatformDataStore dataStore;
    private final ConcurrentMap<String, MutableTenant> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ApiKeyRef> apiKeyIndex = new ConcurrentHashMap<>();
    private final AtomicLong issuedKeySequence = new AtomicLong();
    private final SecureRandom secureRandom = new SecureRandom();

    public AiTenantService(ServerConfiguration configuration) {
        this.configuration = configuration;
        bootstrapDemoTenant();
    }

    public AiTenantService(ServerConfiguration configuration, AiPlatformDataStore dataStore) {
        this.configuration = configuration;
        this.dataStore = dataStore;
        if (!loadFromDisk()) {
            bootstrapDemoTenant();
        }
    }

    public void setDataStore(AiPlatformDataStore dataStore) {
        this.dataStore = dataStore;
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

    public synchronized AiTenantProfile setAllowedModels(String tenantId, List<String> modelNames) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        tenant.allowedModels.clear();
        if (modelNames != null) {
            modelNames.stream()
                    .map(model -> model == null ? "" : model.trim())
                    .filter(model -> !model.isBlank())
                    .map(AiTenantService::normalizeKey)
                    .forEach(tenant.allowedModels::add);
        }
        persistTenants();
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
        persistTenants();
        return snapshotTenant(tenant);
    }

    public synchronized void removeTenant(String tenantId) {
        MutableTenant removed = tenants.remove(normalizeKey(tenantId));
        if (removed == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        // Clean up API key index entries for this tenant
        for (MutableApiKey key : removed.apiKeys.values()) {
            apiKeyIndex.remove(key.secret);
        }
        persistTenants();
    }

    public synchronized AiTenantIssuedKey issueApiKey(String tenantId, String label) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        long now = System.currentTimeMillis();
        AiTenantIssuedKey issuedKey = createApiKey(tenant, label, now);
        persistTenants();
        return issuedKey;
    }

    public synchronized AiTenantKeyRotationResult rotateApiKey(String tenantId, String keyId, String label, long graceSeconds) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        MutableApiKey oldKey = tenant.apiKeys.get(normalizeKey(keyId));
        if (oldKey == null) {
            throw new NoSuchElementException("API key not found: " + keyId);
        }
        long now = System.currentTimeMillis();
        if (!oldKey.active || (oldKey.expiresAtEpochMillis > 0 && oldKey.expiresAtEpochMillis <= now)) {
            oldKey.active = false;
            apiKeyIndex.remove(oldKey.secret);
            persistTenants();
            throw new IllegalStateException("API key is inactive");
        }
        long graceExpiresAt = 0L;
        boolean oldKeyStillActive = graceSeconds > 0;
        if (oldKeyStillActive) {
            graceExpiresAt = now + (graceSeconds * 1000L);
            oldKey.expiresAtEpochMillis = graceExpiresAt;
        } else {
            oldKey.active = false;
            oldKey.expiresAtEpochMillis = now;
            apiKeyIndex.remove(oldKey.secret);
        }
        String nextLabel = label == null || label.isBlank() ? oldKey.label + "-rotated" : label;
        AiTenantIssuedKey newKey = createApiKey(tenant, nextLabel, now);
        persistTenants();
        return new AiTenantKeyRotationResult(newKey, oldKey.keyId, graceExpiresAt, oldKeyStillActive);
    }

    public synchronized void revokeApiKey(String tenantId, String keyId) {
        MutableTenant tenant = tenants.get(normalizeKey(tenantId));
        if (tenant == null) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
        MutableApiKey apiKey = tenant.apiKeys.get(normalizeKey(keyId));
        if (apiKey == null) {
            throw new NoSuchElementException("API key not found: " + keyId);
        }
        apiKey.active = false;
        apiKeyIndex.remove(apiKey.secret);
        persistTenants();
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
        if (key.expiresAtEpochMillis > 0 && key.expiresAtEpochMillis <= now) {
            key.active = false;
            apiKeyIndex.remove(key.secret);
            persistTenants();
            throw new SecurityException("API key expired");
        }
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
        persistTenants();
    }

    public synchronized void assertModelAllowed(AiTenantAccessGrant grant, String modelName) {
        if (grant == null || !grant.tracked() || modelName == null || modelName.isBlank()) {
            return;
        }
        MutableTenant tenant = tenants.get(normalizeKey(grant.tenantId()));
        if (tenant == null || tenant.allowedModels.isEmpty()) {
            return;
        }
        if (!tenant.allowedModels.contains(normalizeKey(modelName))) {
            throw new SecurityException("Model is not allowed for tenant " + grant.tenantId() + ": " + modelName);
        }
    }

    private AiTenantIssuedKey createApiKey(MutableTenant tenant, String label, long now) {
        String normalizedLabel = label == null || label.isBlank() ? "default" : label.trim();
        String keyId = "key-" + Long.toString(issuedKeySequence.incrementAndGet(), 36);
        byte[] randomBytes = new byte[6];
        secureRandom.nextBytes(randomBytes);
        String secret = "vtk_" + normalizeKey(tenant.tenantId) + "_" + HexFormat.of().formatHex(randomBytes);
        MutableApiKey apiKey = new MutableApiKey(keyId, normalizedLabel, secret, now);
        tenant.apiKeys.put(normalizeKey(keyId), apiKey);
        apiKeyIndex.put(secret, new ApiKeyRef(normalizeKey(tenant.tenantId), normalizeKey(keyId)));
        return new AiTenantIssuedKey(tenant.tenantId, tenant.displayName, tenant.plan, keyId, normalizedLabel, secret, now);
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
                        key.lastUsedAtEpochMillis,
                        key.expiresAtEpochMillis
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
                tenant.allowedModels.stream().sorted().toList(),
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
        private final java.util.Set<String> allowedModels = ConcurrentHashMap.newKeySet();
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
        private long expiresAtEpochMillis;

        private MutableApiKey(String keyId, String label, String secret, long createdAtEpochMillis) {
            this.keyId = keyId;
            this.label = label;
            this.secret = secret;
            this.createdAtEpochMillis = createdAtEpochMillis;
        }
    }


    private boolean loadFromDisk() {
        if (dataStore == null) return false;
        try {
            List<TenantData> saved = dataStore.loadList(TENANTS_FILE, new TypeReference<>() {});
            if (saved == null || saved.isEmpty()) return false;
            for (TenantData td : saved) {
                long now = td.createdAt() > 0 ? td.createdAt() : System.currentTimeMillis();
                MutableTenant tenant = new MutableTenant(td.tenantId(), now);
                tenant.displayName = td.displayName();
                tenant.plan = td.plan();
                tenant.active = td.active();
                tenant.rateLimitPerMinute = td.rateLimitPerMinute();
                tenant.tokenQuota = td.tokenQuota();
                tenant.totalRequests = td.totalRequests();
                tenant.totalTokens = td.totalTokens();
                tenant.currentWindowEpochMinute = td.currentWindowEpochMinute();
                tenant.currentWindowRequests = td.currentWindowRequests();
                tenant.lastActivityEpochMillis = td.lastActivityAt();
                tenants.put(normalizeKey(td.tenantId()), tenant);
                if (td.allowedModels() != null) {
                    td.allowedModels().stream()
                            .map(AiTenantService::normalizeKey)
                            .filter(model -> !model.isBlank())
                            .forEach(tenant.allowedModels::add);
                }
                if (td.apiKeys() != null) {
                    for (TenantData.ApiKeyData akd : td.apiKeys()) {
                        if (isLegacyDemoKey(td.tenantId(), akd.secret())) {
                            continue;
                        }
                        MutableApiKey apiKey = new MutableApiKey(akd.keyId(), akd.label(), akd.secret(), akd.createdAt());
                        apiKey.active = akd.active();
                        apiKey.lastUsedAtEpochMillis = akd.lastUsedAt();
                        apiKey.expiresAtEpochMillis = akd.expiresAt();
                        tenant.apiKeys.put(normalizeKey(akd.keyId()), apiKey);
                        issuedKeySequence.updateAndGet(current -> Math.max(current, sequenceFromKeyId(akd.keyId())));
                        if (apiKey.active && (apiKey.expiresAtEpochMillis <= 0 || apiKey.expiresAtEpochMillis > System.currentTimeMillis())) {
                            apiKeyIndex.put(akd.secret(), new ApiKeyRef(normalizeKey(td.tenantId()), normalizeKey(akd.keyId())));
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLegacyDemoKey(String tenantId, String secret) {
        return DEMO_TENANT_ID.equals(normalizeKey(tenantId))
                && LEGACY_DEMO_API_KEY.equals(secret);
    }

    private void persistTenants() {
        if (dataStore == null) return;
        try {
            List<TenantData> data = new java.util.ArrayList<>();
            for (MutableTenant t : tenants.values()) {
                List<TenantData.ApiKeyData> keys = t.apiKeys.values().stream()
                        .map(k -> new TenantData.ApiKeyData(k.keyId, k.label, k.secret, k.active,
                                k.createdAtEpochMillis, k.lastUsedAtEpochMillis, k.expiresAtEpochMillis))
                        .toList();
                data.add(new TenantData(t.tenantId, t.displayName, t.plan, t.active,
                        t.rateLimitPerMinute, t.tokenQuota, t.createdAtEpochMillis,
                        t.totalRequests, t.totalTokens, t.currentWindowEpochMinute,
                        t.currentWindowRequests, t.lastActivityEpochMillis, keys,
                        new ArrayList<>(t.allowedModels)));
            }
            dataStore.save(TENANTS_FILE, data);
        } catch (Exception ignored) {
        }
    }

    private static long sequenceFromKeyId(String keyId) {
        if (keyId == null || !keyId.startsWith("key-")) {
            return 0L;
        }
        try {
            return Long.parseLong(keyId.substring(4), 36);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
