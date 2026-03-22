package io.velo.was.aiplatform.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import io.velo.was.aiplatform.intent.IntentKeyword;
import io.velo.was.aiplatform.intent.IntentType;
import io.velo.was.aiplatform.intent.RoutingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiPlatformDataStoreTest {

    @TempDir
    Path tempDir;

    private AiPlatformDataStore dataStore;

    @BeforeEach
    void setUp() {
        dataStore = new AiPlatformDataStore(tempDir);
    }

    @Test
    void dataDirectoryCreatedOnInit() {
        assertTrue(dataStore.getDataDir().toFile().exists());
        assertTrue(dataStore.getDataDir().toFile().isDirectory());
    }

    @Test
    void saveAndLoadKeywordsRoundTrip() {
        List<IntentKeyword> keywords = List.of(
                new IntentKeyword("kw-1", "요약", List.of("정리", "핵심"), IntentType.SUMMARIZATION, 100, true, 1000L),
                new IntentKeyword("kw-2", "코드", List.of("함수", "SQL"), IntentType.CODE, 90, true, 2000L)
        );

        dataStore.save("test-keywords.json", keywords);
        assertTrue(dataStore.exists("test-keywords.json"));

        List<IntentKeyword> loaded = dataStore.loadList("test-keywords.json", new TypeReference<>() {});
        assertNotNull(loaded);
        assertEquals(2, loaded.size());
        assertEquals("요약", loaded.get(0).primaryKeyword());
        assertEquals(IntentType.SUMMARIZATION, loaded.get(0).intent());
        assertEquals(List.of("정리", "핵심"), loaded.get(0).synonyms());
        assertEquals("코드", loaded.get(1).primaryKeyword());
    }

    @Test
    void saveAndLoadPoliciesRoundTrip() {
        List<RoutingPolicy> policies = List.of(
                new RoutingPolicy("pol-1", IntentType.SUMMARIZATION, 100, "vllm-long", "qwen-long", "llm-general", false, true, "", Integer.MAX_VALUE, 1000L),
                new RoutingPolicy("pol-2", IntentType.CODE, 90, "vllm-code", "codestral", "llm-general", false, true, "", Integer.MAX_VALUE, 2000L)
        );

        dataStore.save("test-policies.json", policies);

        List<RoutingPolicy> loaded = dataStore.loadList("test-policies.json", new TypeReference<>() {});
        assertNotNull(loaded);
        assertEquals(2, loaded.size());
        assertEquals("vllm-long", loaded.get(0).routeTarget());
        assertEquals("codestral", loaded.get(1).modelName());
    }

    @Test
    void saveAndLoadModelDataRoundTrip() throws Exception {
        List<ModelData> models = List.of(
                new ModelData("test-model", "LLM", "openai", "runtime", 1000L,
                        List.of(new ModelData.VersionData("v1", "balanced", 250, 85, false, true, "ACTIVE", 1000L)))
        );

        dataStore.save("test-models.json", models);

        List<ModelData> loaded = dataStore.getMapper().readValue(
                dataStore.getDataDir().resolve("test-models.json").toFile(),
                dataStore.getMapper().getTypeFactory().constructCollectionType(List.class, ModelData.class));
        assertNotNull(loaded);
        assertEquals(1, loaded.size());
        assertEquals("test-model", loaded.get(0).name());
        assertEquals("runtime", loaded.get(0).source());
        assertEquals("v1", loaded.get(0).versions().get(0).version());
    }

    @Test
    void saveAndLoadTenantDataRoundTrip() {
        List<TenantData> tenants = List.of(
                new TenantData("t-1", "Test Tenant", "pro", true, 120, 500000, 1000L,
                        List.of(new TenantData.ApiKeyData("k-1", "default", "vtk_secret_123", true, 1000L)))
        );

        dataStore.save("test-tenants.json", tenants);

        List<TenantData> loaded = dataStore.loadList("test-tenants.json", new TypeReference<>() {});
        assertNotNull(loaded);
        assertEquals(1, loaded.size());
        assertEquals("t-1", loaded.get(0).tenantId());
        assertEquals("pro", loaded.get(0).plan());
        assertEquals("vtk_secret_123", loaded.get(0).apiKeys().get(0).secret());
    }

    @Test
    void loadNonExistentFileReturnsNull() {
        assertNull(dataStore.loadList("nonexistent.json", new TypeReference<List<IntentKeyword>>() {}));
    }

    @Test
    void existsReturnsFalseForMissingFile() {
        assertFalse(dataStore.exists("missing.json"));
    }

    @Test
    void overwriteExistingFile() {
        dataStore.save("overwrite.json", List.of(new IntentKeyword("kw-1", "first", List.of(), IntentType.GENERAL, 1, true, 1L)));
        dataStore.save("overwrite.json", List.of(
                new IntentKeyword("kw-2", "second", List.of(), IntentType.CODE, 2, true, 2L),
                new IntentKeyword("kw-3", "third", List.of(), IntentType.SEARCH, 3, true, 3L)
        ));

        List<IntentKeyword> loaded = dataStore.loadList("overwrite.json", new TypeReference<>() {});
        assertEquals(2, loaded.size());
        assertEquals("second", loaded.get(0).primaryKeyword());
    }
}
