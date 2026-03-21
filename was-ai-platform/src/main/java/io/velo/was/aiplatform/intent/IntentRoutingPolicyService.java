package io.velo.was.aiplatform.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import io.velo.was.aiplatform.persistence.AiPlatformDataStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 의도 기반 라우팅 정책 서비스.
 * 키워드, 라우팅 정책의 CRUD와 메모리 캐시 관리를 담당.
 * JSON 파일 기반 영속화로 서버 재시작 시에도 설정 유지.
 */
public class IntentRoutingPolicyService {

    private static final Logger LOG = Logger.getLogger(IntentRoutingPolicyService.class.getName());
    private static final String KEYWORDS_FILE = "keywords.json";
    private static final String POLICIES_FILE = "policies.json";

    private final ConcurrentHashMap<String, IntentKeyword> keywords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RoutingPolicy> policies = new ConcurrentHashMap<>();
    private final AtomicLong keywordSeq = new AtomicLong();
    private final AtomicLong policySeq = new AtomicLong();
    private volatile AiPlatformDataStore dataStore;

    // 캐시: 의도별 정책 (의도 변경 시 무효화)
    private volatile Map<IntentType, List<RoutingPolicy>> policyCache = Map.of();
    private volatile List<IntentKeyword> keywordCache = List.of();

    public IntentRoutingPolicyService() {
        bootstrapDefaults();
        rebuildCache();
    }

    public IntentRoutingPolicyService(AiPlatformDataStore dataStore) {
        this.dataStore = dataStore;
        if (!loadFromDisk()) {
            bootstrapDefaults();
            LOG.info("Intent routing: bootstrapped defaults (no saved data found)");
        } else {
            LOG.info("Intent routing: loaded " + keywords.size() + " keywords, " + policies.size() + " policies from disk");
        }
        rebuildCache();
    }

    public void setDataStore(AiPlatformDataStore dataStore) {
        this.dataStore = dataStore;
    }

    // ── 키워드 CRUD ──

    public synchronized IntentKeyword addKeyword(String primaryKeyword, List<String> synonyms,
                                                  IntentType intent, int priority) {
        String id = "kw-" + Long.toString(keywordSeq.incrementAndGet(), 36);
        IntentKeyword keyword = new IntentKeyword(id, primaryKeyword, synonyms, intent, priority, true, System.currentTimeMillis());
        keywords.put(id, keyword);
        rebuildCache();
        persistKeywords();
        return keyword;
    }

    public synchronized IntentKeyword updateKeyword(String keywordId, String primaryKeyword, List<String> synonyms,
                                                     IntentType intent, int priority, boolean enabled) {
        IntentKeyword existing = keywords.get(keywordId);
        if (existing == null) {
            throw new NoSuchElementException("키워드를 찾을 수 없습니다: " + keywordId);
        }
        IntentKeyword updated = new IntentKeyword(keywordId, primaryKeyword, synonyms, intent, priority, enabled, existing.createdAt());
        keywords.put(keywordId, updated);
        rebuildCache();
        persistKeywords();
        return updated;
    }

    public synchronized void removeKeyword(String keywordId) {
        if (keywords.remove(keywordId) == null) {
            throw new NoSuchElementException("키워드를 찾을 수 없습니다: " + keywordId);
        }
        rebuildCache();
        persistKeywords();
    }

    public List<IntentKeyword> listKeywords() {
        return keywordCache;
    }

    public IntentKeyword getKeyword(String keywordId) {
        IntentKeyword kw = keywords.get(keywordId);
        if (kw == null) {
            throw new NoSuchElementException("키워드를 찾을 수 없습니다: " + keywordId);
        }
        return kw;
    }

    // ── 정책 CRUD ──

    public synchronized RoutingPolicy addPolicy(IntentType intent, int priority, String routeTarget,
                                                 String modelName, String fallbackModel,
                                                 boolean streamingPreferred, String tenantOverride, int maxInputTokens) {
        String id = "pol-" + Long.toString(policySeq.incrementAndGet(), 36);
        RoutingPolicy policy = new RoutingPolicy(id, intent, priority, routeTarget, modelName, fallbackModel,
                streamingPreferred, true, tenantOverride, maxInputTokens, System.currentTimeMillis());
        policies.put(id, policy);
        rebuildCache();
        persistPolicies();
        return policy;
    }

    public synchronized RoutingPolicy updatePolicy(String policyId, IntentType intent, int priority, String routeTarget,
                                                    String modelName, String fallbackModel,
                                                    boolean streamingPreferred, boolean enabled,
                                                    String tenantOverride, int maxInputTokens) {
        RoutingPolicy existing = policies.get(policyId);
        if (existing == null) {
            throw new NoSuchElementException("정책을 찾을 수 없습니다: " + policyId);
        }
        RoutingPolicy updated = new RoutingPolicy(policyId, intent, priority, routeTarget, modelName, fallbackModel,
                streamingPreferred, enabled, tenantOverride, maxInputTokens, existing.createdAt());
        policies.put(policyId, updated);
        rebuildCache();
        persistPolicies();
        return updated;
    }

    public synchronized void removePolicy(String policyId) {
        if (policies.remove(policyId) == null) {
            throw new NoSuchElementException("정책을 찾을 수 없습니다: " + policyId);
        }
        rebuildCache();
        persistPolicies();
    }

    public List<RoutingPolicy> listPolicies() {
        return policies.values().stream()
                .sorted(Comparator.comparingInt(RoutingPolicy::priority).reversed())
                .toList();
    }

    public List<RoutingPolicy> getPoliciesForIntent(IntentType intent) {
        return policyCache.getOrDefault(intent, List.of());
    }

    // ── 캐시 관리 ──

    public synchronized void invalidateCache() {
        rebuildCache();
    }

    public int keywordCount() {
        return keywords.size();
    }

    public int policyCount() {
        return policies.size();
    }

    private void rebuildCache() {
        // 키워드 캐시: 우선순위 내림차순
        keywordCache = keywords.values().stream()
                .sorted(Comparator.comparingInt(IntentKeyword::priority).reversed())
                .toList();

        // 정책 캐시: 의도별 그룹핑, 우선순위 내림차순
        Map<IntentType, List<RoutingPolicy>> newCache = new HashMap<>();
        for (RoutingPolicy policy : policies.values()) {
            if (policy.enabled()) {
                newCache.computeIfAbsent(policy.intent(), k -> new ArrayList<>()).add(policy);
            }
        }
        for (List<RoutingPolicy> list : newCache.values()) {
            list.sort(Comparator.comparingInt(RoutingPolicy::priority).reversed());
        }
        policyCache = Map.copyOf(newCache.entrySet().stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), List.copyOf(e.getValue())), HashMap::putAll));
    }

    // ── 기본 키워드/정책 부트스트랩 ──

    private void bootstrapDefaults() {
        long now = System.currentTimeMillis();

        // 요약 키워드
        keywords.put("kw-sum", new IntentKeyword("kw-sum", "요약", List.of("정리", "핵심", "summarize", "summary", "간추려"), IntentType.SUMMARIZATION, 100, true, now));
        // 생성 키워드
        keywords.put("kw-gen", new IntentKeyword("kw-gen", "작성", List.of("만들어", "초안", "generate", "draft", "생성", "써줘"), IntentType.GENERATION, 95, true, now));
        // 코드 키워드
        keywords.put("kw-code", new IntentKeyword("kw-code", "코드", List.of("함수", "sql", "쿼리", "script", "프로그램", "코딩", "code"), IntentType.CODE, 90, true, now));
        // 분류 키워드
        keywords.put("kw-cls", new IntentKeyword("kw-cls", "분류", List.of("카테고리", "판별", "classify", "분석", "구분"), IntentType.CLASSIFICATION, 85, true, now));
        // 추출 키워드
        keywords.put("kw-ext", new IntentKeyword("kw-ext", "추출", List.of("뽑아", "항목화", "extract", "파싱", "추려"), IntentType.EXTRACTION, 85, true, now));
        // 검색 키워드
        keywords.put("kw-search", new IntentKeyword("kw-search", "검색", List.of("찾아", "근거", "문서", "조회", "search", "find"), IntentType.SEARCH, 80, true, now));
        // 검증 키워드
        keywords.put("kw-val", new IntentKeyword("kw-val", "검토", List.of("점검", "리스크", "오류", "확인", "validate", "review", "검증"), IntentType.VALIDATION, 80, true, now));
        // 번역 키워드
        keywords.put("kw-trans", new IntentKeyword("kw-trans", "번역", List.of("영문", "한글로", "translate", "영어로", "일본어로"), IntentType.TRANSLATION, 75, true, now));
        // 대화 키워드
        keywords.put("kw-conv", new IntentKeyword("kw-conv", "대화", List.of("안녕", "chat", "상담", "질문", "답변"), IntentType.CONVERSATION, 50, true, now));

        // 기본 라우팅 정책
        policies.put("pol-sum", new RoutingPolicy("pol-sum", IntentType.SUMMARIZATION, 100, "vllm-long", "qwen-long", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-gen", new RoutingPolicy("pol-gen", IntentType.GENERATION, 95, "vllm-premium", "deepseek-large", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-code", new RoutingPolicy("pol-code", IntentType.CODE, 90, "vllm-code", "codestral", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-cls", new RoutingPolicy("pol-cls", IntentType.CLASSIFICATION, 85, "vllm-fast", "small-classifier", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-ext", new RoutingPolicy("pol-ext", IntentType.EXTRACTION, 85, "vllm-structured", "extract-model", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-search", new RoutingPolicy("pol-search", IntentType.SEARCH, 80, "rag-pipeline", "embed-rerank-llm", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-val", new RoutingPolicy("pol-val", IntentType.VALIDATION, 80, "vllm-reasoning", "reasoning-model", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-trans", new RoutingPolicy("pol-trans", IntentType.TRANSLATION, 75, "vllm-translate", "nllb-large", "llm-general", false, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-conv", new RoutingPolicy("pol-conv", IntentType.CONVERSATION, 50, "vllm-balanced", "llm-general", "llm-general", true, true, "", Integer.MAX_VALUE, now));
        policies.put("pol-default", new RoutingPolicy("pol-default", IntentType.GENERAL, 10, "default", "llm-general", "llm-general", false, true, "", Integer.MAX_VALUE, now));

        keywordSeq.set(20);
        policySeq.set(20);
    }

    // ── 영속화 (Jackson) ──

    private boolean loadFromDisk() {
        if (dataStore == null) return false;
        boolean loaded = false;
        try {
            List<IntentKeyword> savedKeywords = dataStore.loadList(KEYWORDS_FILE, new TypeReference<>() {});
            if (savedKeywords != null && !savedKeywords.isEmpty()) {
                for (IntentKeyword kw : savedKeywords) {
                    keywords.put(kw.keywordId(), kw);
                }
                long maxSeq = savedKeywords.stream()
                        .map(kw -> kw.keywordId().replace("kw-", ""))
                        .mapToLong(s -> { try { return Long.parseLong(s, 36); } catch (NumberFormatException e) { return 0; } })
                        .max().orElse(20);
                keywordSeq.set(Math.max(maxSeq + 1, 20));
                loaded = true;
            }
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Failed to load keywords", e);
        }
        try {
            List<RoutingPolicy> savedPolicies = dataStore.loadList(POLICIES_FILE, new TypeReference<>() {});
            if (savedPolicies != null && !savedPolicies.isEmpty()) {
                for (RoutingPolicy pol : savedPolicies) {
                    policies.put(pol.policyId(), pol);
                }
                long maxSeq = savedPolicies.stream()
                        .map(p -> p.policyId().replace("pol-", ""))
                        .mapToLong(s -> { try { return Long.parseLong(s, 36); } catch (NumberFormatException e) { return 0; } })
                        .max().orElse(20);
                policySeq.set(Math.max(maxSeq + 1, 20));
                loaded = loaded || true;
            }
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Failed to load policies", e);
        }
        return loaded;
    }

    private void persistKeywords() {
        if (dataStore == null) return;
        try {
            dataStore.save(KEYWORDS_FILE, new ArrayList<>(keywords.values()));
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Failed to persist keywords", e);
        }
    }

    private void persistPolicies() {
        if (dataStore == null) return;
        try {
            dataStore.save(POLICIES_FILE, new ArrayList<>(policies.values()));
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Failed to persist policies", e);
        }
    }
}
