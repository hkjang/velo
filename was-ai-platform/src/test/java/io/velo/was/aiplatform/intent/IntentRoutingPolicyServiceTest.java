package io.velo.was.aiplatform.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class IntentRoutingPolicyServiceTest {

    private IntentRoutingPolicyService service;

    @BeforeEach
    void setUp() {
        service = new IntentRoutingPolicyService(); // no-arg = bootstrap defaults
    }

    @Test
    void bootstrapLoadsDefaultKeywordsAndPolicies() {
        assertTrue(service.keywordCount() >= 9, "Should bootstrap at least 9 keywords");
        assertTrue(service.policyCount() >= 10, "Should bootstrap at least 10 policies");
    }

    @Test
    void listKeywordsReturnsOrderedByPriority() {
        List<IntentKeyword> keywords = service.listKeywords();
        assertFalse(keywords.isEmpty());
        for (int i = 1; i < keywords.size(); i++) {
            assertTrue(keywords.get(i - 1).priority() >= keywords.get(i).priority(),
                    "Keywords should be sorted by priority descending");
        }
    }

    @Test
    void addKeywordIncrementsCount() {
        int before = service.keywordCount();
        IntentKeyword kw = service.addKeyword("테스트", List.of("test", "시험"), IntentType.GENERAL, 42);
        assertEquals(before + 1, service.keywordCount());
        assertNotNull(kw.keywordId());
        assertEquals("테스트", kw.primaryKeyword());
        assertEquals(IntentType.GENERAL, kw.intent());
        assertEquals(42, kw.priority());
        assertTrue(kw.enabled());
    }

    @Test
    void updateKeywordChangesFields() {
        IntentKeyword kw = service.addKeyword("원래", List.of(), IntentType.CODE, 50);
        IntentKeyword updated = service.updateKeyword(kw.keywordId(), "수정됨", List.of("changed"), IntentType.SEARCH, 99, false);
        assertEquals("수정됨", updated.primaryKeyword());
        assertEquals(IntentType.SEARCH, updated.intent());
        assertEquals(99, updated.priority());
        assertFalse(updated.enabled());
        assertEquals(List.of("changed"), updated.synonyms());
    }

    @Test
    void removeKeywordDecrementsCount() {
        IntentKeyword kw = service.addKeyword("삭제대상", List.of(), IntentType.GENERAL, 10);
        int before = service.keywordCount();
        service.removeKeyword(kw.keywordId());
        assertEquals(before - 1, service.keywordCount());
    }

    @Test
    void removeNonExistentKeywordThrows() {
        assertThrows(NoSuchElementException.class, () -> service.removeKeyword("nonexistent"));
    }

    @Test
    void addPolicyAndRetrieveByIntent() {
        RoutingPolicy pol = service.addPolicy(IntentType.VALIDATION, 77, "vllm-guard", "guard-model", "fallback", false, "", 0);
        assertNotNull(pol.policyId());
        List<RoutingPolicy> policies = service.getPoliciesForIntent(IntentType.VALIDATION);
        assertTrue(policies.stream().anyMatch(p -> p.policyId().equals(pol.policyId())));
    }

    @Test
    void updatePolicyChangesFields() {
        RoutingPolicy pol = service.addPolicy(IntentType.CODE, 60, "target-a", "model-a", "fb-a", false, "", 0);
        RoutingPolicy updated = service.updatePolicy(pol.policyId(), IntentType.SEARCH, 80, "target-b", "model-b", "fb-b", true, true, "tenant-x", 5000);
        assertEquals(IntentType.SEARCH, updated.intent());
        assertEquals(80, updated.priority());
        assertEquals("target-b", updated.routeTarget());
        assertEquals("model-b", updated.modelName());
        assertTrue(updated.streamingPreferred());
    }

    @Test
    void removePolicyRemovesFromCache() {
        RoutingPolicy pol = service.addPolicy(IntentType.EXTRACTION, 55, "t", "m", "f", false, "", 0);
        service.removePolicy(pol.policyId());
        List<RoutingPolicy> all = service.listPolicies();
        assertFalse(all.stream().anyMatch(p -> p.policyId().equals(pol.policyId())));
    }

    @Test
    void cacheInvalidationRebuildsCorrectly() {
        int keywordsBefore = service.listKeywords().size();
        service.addKeyword("캐시테스트", List.of(), IntentType.GENERAL, 1);
        assertEquals(keywordsBefore + 1, service.listKeywords().size());
        service.invalidateCache();
        assertEquals(keywordsBefore + 1, service.listKeywords().size());
    }
}
