package io.velo.was.aiplatform.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 의도 기반 라우팅 감사 로거.
 * 라우팅 결정 이력을 메모리에 보관하고 조회 기능 제공.
 * 최대 1000건 유지 (FIFO).
 */
public class RouteAuditLogger {

    private static final int MAX_ENTRIES = 1000;

    private final ConcurrentLinkedDeque<RouteAuditEntry> entries = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalCount = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();

    /**
     * 라우팅 결정을 감사 로그에 기록.
     *
     * @param tenantId    테넌트 ID
     * @param prompt      원본 프롬프트
     * @param normalized  정규화된 텍스트
     * @param decision    라우팅 결정
     * @param usedFallback fallback 모델 사용 여부
     * @param responseTimeMs 실제 응답 시간 (ms)
     * @return 생성된 감사 엔트리
     */
    public RouteAuditEntry log(String tenantId, String prompt, String normalized,
                               IntentRouteDecision decision, boolean usedFallback, long responseTimeMs) {
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        RouteAuditEntry entry = new RouteAuditEntry(
                requestId,
                tenantId != null ? tenantId : "unknown",
                truncate(prompt, 200),
                truncate(normalized, 200),
                decision.resolvedIntent(),
                decision.matchedKeyword(),
                decision.policyId(),
                decision.routeTarget(),
                decision.modelName(),
                usedFallback,
                decision.processingTimeNanos(),
                responseTimeMs,
                System.currentTimeMillis()
        );

        entries.addFirst(entry);
        totalCount.incrementAndGet();
        if (usedFallback) {
            fallbackCount.incrementAndGet();
        }

        // FIFO: 최대 개수 유지
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }

        return entry;
    }

    /**
     * 최근 감사 로그 조회.
     */
    public List<RouteAuditEntry> recentEntries(int limit) {
        List<RouteAuditEntry> result = new ArrayList<>();
        int count = 0;
        for (RouteAuditEntry entry : entries) {
            if (count >= limit) break;
            result.add(entry);
            count++;
        }
        return List.copyOf(result);
    }

    /**
     * 의도별 통계 조회.
     */
    public IntentStats stats() {
        long total = totalCount.get();
        long fallbacks = fallbackCount.get();
        java.util.Map<IntentType, Long> intentCounts = new java.util.EnumMap<>(IntentType.class);
        long totalProcessingNanos = 0;
        int counted = 0;
        for (RouteAuditEntry entry : entries) {
            intentCounts.merge(entry.resolvedIntent(), 1L, Long::sum);
            totalProcessingNanos += entry.processingTimeNanos();
            counted++;
        }
        double avgProcessingMicros = counted > 0 ? (totalProcessingNanos / counted) / 1000.0 : 0;
        return new IntentStats(total, fallbacks, intentCounts, avgProcessingMicros, entries.size());
    }

    /**
     * 감사 로그 초기화.
     */
    public void clear() {
        entries.clear();
        totalCount.set(0);
        fallbackCount.set(0);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    public record IntentStats(
            long totalRoutes,
            long fallbackRoutes,
            java.util.Map<IntentType, Long> intentDistribution,
            double avgProcessingMicros,
            int auditLogSize
    ) {
    }
}
