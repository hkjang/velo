package io.velo.was.aiplatform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * AI 게이트웨이 요청에 대한 인메모리 링 버퍼 감사 로그.
 *
 * <p>최근 {@code maxEntries}건의 감사 레코드를 유지한다. Thread-safe.
 * 모든 기록은 두 SLF4J 로거로 출력된다:
 * <ul>
 *   <li>{@code io.velo.was.aiplatform.audit.AiGatewayAuditLog} — 표준 애플리케이션 로그 (INFO/WARN)</li>
 *   <li>{@code AI_GATEWAY_AUDIT} — 전용 감사 로거 (JSON Lines 출력,
 *       logback/log4j 설정으로 별도 파일 라우팅 가능)</li>
 * </ul>
 */
public class AiGatewayAuditLog {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayAuditLog.class);

    /** 전용 감사 로거 — JSON Lines 파일 기반 감사 추적용. */
    private static final Logger auditLogger = LoggerFactory.getLogger("AI_GATEWAY_AUDIT");

    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final List<AiGatewayAuditEntry> buffer;
    private final int maxEntries;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile AiGatewayAuditFileLogger fileLogger;

    public AiGatewayAuditLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public AiGatewayAuditLog(int maxEntries) {
        this.maxEntries = maxEntries;
        this.buffer = new ArrayList<>(Math.min(maxEntries, 1024));
    }

    /**
     * 파일 기반 감사 로거를 연결한다.
     * 설정 시 모든 감사 엔트리가 일별 로테이션 파일에도 기록된다.
     */
    public void setFileLogger(AiGatewayAuditFileLogger fileLogger) {
        this.fileLogger = fileLogger;
    }

    /** 연결된 파일 로거 반환 (없으면 null). */
    public AiGatewayAuditFileLogger fileLogger() {
        return fileLogger;
    }

    /**
     * 감사 이벤트를 기록한다.
     */
    public void record(AiGatewayAuditEntry entry) {
        lock.writeLock().lock();
        try {
            if (buffer.size() >= maxEntries) {
                buffer.remove(0);
            }
            buffer.add(entry);
        } finally {
            lock.writeLock().unlock();
        }

        // ── 표준 애플리케이션 로그 ──
        if (entry.success()) {
            log.info("AI audit: endpoint={} model={} tenant={} tokens={} duration={}ms addr={}",
                    entry.endpoint(), entry.modelName(), entry.tenantId(),
                    entry.estimatedTokens(), entry.durationMs(), entry.remoteAddr());
        } else {
            log.warn("AI audit FAIL: endpoint={} model={} tenant={} err={} duration={}ms addr={}",
                    entry.endpoint(), entry.modelName(), entry.tenantId(),
                    entry.errorMsg(), entry.durationMs(), entry.remoteAddr());
        }

        // ── 전용 감사 로그 (JSON line) ──
        if (auditLogger.isInfoEnabled()) {
            auditLogger.info(entry.toJson());
        }

        // ── 파일 기반 영속화 (일별 로테이션 JSON Lines) ──
        AiGatewayAuditFileLogger fl = fileLogger;
        if (fl != null) {
            fl.write(entry);
        }
    }

    /**
     * 성공한 요청을 감사 기록한다.
     */
    public void recordSuccess(String tenantId, String endpoint, String modelName,
                               String provider, String requestType, String prompt,
                               long durationMs, int estimatedTokens, boolean streaming,
                               String remoteAddr, String routePolicy, String intentType,
                               String modality) {
        record(new AiGatewayAuditEntry(
                Instant.now(),
                generateRequestId(),
                tenantId, endpoint, modelName, provider, requestType,
                truncate(prompt, 1000),
                durationMs, estimatedTokens, true, streaming,
                null, remoteAddr, routePolicy, intentType,
                modality != null ? modality : "text"));
    }

    /**
     * 실패한 요청을 감사 기록한다.
     */
    public void recordFailure(String tenantId, String endpoint, String modelName,
                               String provider, String requestType, String prompt,
                               long durationMs, int estimatedTokens, boolean streaming,
                               String errorMsg, String remoteAddr,
                               String routePolicy, String intentType,
                               String modality) {
        record(new AiGatewayAuditEntry(
                Instant.now(),
                generateRequestId(),
                tenantId, endpoint, modelName, provider, requestType,
                truncate(prompt, 1000),
                durationMs, estimatedTokens, false, streaming,
                errorMsg, remoteAddr, routePolicy, intentType,
                modality != null ? modality : "text"));
    }

    /**
     * 최근 감사 엔트리를 역순(최신 먼저)으로 조회한다.
     *
     * @param limit    최대 반환 건수
     * @param endpoint 엔드포인트 필터 (null이면 전체)
     * @param tenantId 테넌트 필터 (null이면 전체)
     * @param modelName 모델명 필터 (null이면 전체)
     */
    public List<AiGatewayAuditEntry> query(int limit, String endpoint, String tenantId, String modelName) {
        return query(limit, endpoint, tenantId, modelName, null);
    }

    /**
     * 최근 감사 엔트리를 역순(최신 먼저)으로 조회한다.
     *
     * @param limit     최대 반환 건수
     * @param endpoint  엔드포인트 필터 (null이면 전체)
     * @param tenantId  테넌트 필터 (null이면 전체)
     * @param modelName 모델명 필터 (null이면 전체)
     * @param modality  모달리티 필터 (null이면 전체)
     */
    public List<AiGatewayAuditEntry> query(int limit, String endpoint, String tenantId,
                                            String modelName, String modality) {
        lock.readLock().lock();
        try {
            List<AiGatewayAuditEntry> result = new ArrayList<>();
            for (int i = buffer.size() - 1; i >= 0 && result.size() < limit; i--) {
                AiGatewayAuditEntry e = buffer.get(i);
                if (endpoint != null && !endpoint.equals(e.endpoint())) continue;
                if (tenantId != null && !tenantId.equals(e.tenantId())) continue;
                if (modelName != null && !modelName.equals(e.modelName())) continue;
                if (modality != null && !modality.equals(e.modality())) continue;
                result.add(e);
            }
            return Collections.unmodifiableList(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 버퍼에 저장된 총 엔트리 수. */
    public int size() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 감사 통계를 계산한다.
     */
    public AuditStats stats() {
        lock.readLock().lock();
        try {
            long total = buffer.size();
            long successCount = 0;
            long totalDuration = 0;
            long totalTokens = 0;
            java.util.Map<String, Long> endpointCounts = new java.util.LinkedHashMap<>();
            java.util.Map<String, Long> modelCounts = new java.util.LinkedHashMap<>();

            java.util.Map<String, Long> modalityCounts = new java.util.LinkedHashMap<>();

            for (AiGatewayAuditEntry e : buffer) {
                if (e.success()) successCount++;
                totalDuration += e.durationMs();
                totalTokens += e.estimatedTokens();
                endpointCounts.merge(e.endpoint() != null ? e.endpoint() : "unknown", 1L, Long::sum);
                modelCounts.merge(e.modelName() != null ? e.modelName() : "unknown", 1L, Long::sum);
                modalityCounts.merge(e.modality() != null ? e.modality() : "text", 1L, Long::sum);
            }

            double avgDuration = total > 0 ? (double) totalDuration / total : 0;
            double successRate = total > 0 ? (double) successCount / total * 100.0 : 0;
            return new AuditStats(total, successCount, successRate, avgDuration, totalTokens, endpointCounts, modelCounts, modalityCounts);
        } finally {
            lock.readLock().unlock();
        }
    }

    private static String generateRequestId() {
        return "ai-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /**
     * 감사 통계 스냅샷.
     */
    public record AuditStats(
            long totalEntries,
            long successCount,
            double successRate,
            double avgDurationMs,
            long totalTokens,
            java.util.Map<String, Long> endpointDistribution,
            java.util.Map<String, Long> modelDistribution,
            java.util.Map<String, Long> modalityDistribution
    ) {
    }
}
