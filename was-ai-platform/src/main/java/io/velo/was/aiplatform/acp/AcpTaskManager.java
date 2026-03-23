package io.velo.was.aiplatform.acp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ACP 태스크 생명주기 관리자.
 * 인메모리 태스크 저장소 + 감사 로깅 + SSE 구독 관리.
 */
public class AcpTaskManager {

    private static final Logger log = LoggerFactory.getLogger(AcpTaskManager.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("ACP_AUDIT");

    private static final int MAX_TASKS = 10_000;
    private static final int MAX_AUDIT = 5_000;

    private final ConcurrentHashMap<String, AcpTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<AcpAuditEntry> auditLog = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalCreated = new AtomicLong();
    private final AtomicLong totalCompleted = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();

    /**
     * 새 태스크를 생성한다.
     */
    public AcpTask createTask(String fromAgent, String toAgent, AcpMessage input, Map<String, String> metadata) {
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 12);
        AcpTask task = new AcpTask(taskId, fromAgent, toAgent, input, metadata);
        tasks.put(taskId, task);
        totalCreated.incrementAndGet();
        evictOldTasks();
        audit("TASK_CREATED", taskId, fromAgent, toAgent, null, "SUBMITTED", 0, true, null, null);
        log.info("ACP task created: {} from={} to={}", taskId, fromAgent, toAgent);
        return task;
    }

    /**
     * 태스크를 조회한다.
     */
    public AcpTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 태스크 상태를 WORKING으로 전환한다.
     */
    public AcpTask startTask(String taskId) {
        AcpTask task = tasks.get(taskId);
        if (task == null) return null;
        if (task.start()) {
            audit("TASK_STARTED", taskId, task.fromAgent(), task.toAgent(), null, "WORKING", 0, true, null, null);
        }
        return task;
    }

    /**
     * 태스크를 완료한다.
     */
    public AcpTask completeTask(String taskId, AcpMessage output) {
        AcpTask task = tasks.get(taskId);
        if (task == null) return null;
        long duration = java.time.Duration.between(task.createdAt(), Instant.now()).toMillis();
        if (task.complete(output)) {
            totalCompleted.incrementAndGet();
            audit("TASK_COMPLETED", taskId, task.fromAgent(), task.toAgent(), null, "COMPLETED", duration, true, null, null);
            log.info("ACP task completed: {} duration={}ms", taskId, duration);
        }
        return task;
    }

    /**
     * 태스크를 실패 처리한다.
     */
    public AcpTask failTask(String taskId, String errorMsg) {
        AcpTask task = tasks.get(taskId);
        if (task == null) return null;
        long duration = java.time.Duration.between(task.createdAt(), Instant.now()).toMillis();
        if (task.fail(errorMsg)) {
            totalFailed.incrementAndGet();
            audit("TASK_FAILED", taskId, task.fromAgent(), task.toAgent(), null, "FAILED", duration, false, errorMsg, null);
            log.warn("ACP task failed: {} error={}", taskId, errorMsg);
        }
        return task;
    }

    /**
     * 태스크를 취소한다.
     */
    public AcpTask cancelTask(String taskId) {
        AcpTask task = tasks.get(taskId);
        if (task == null) return null;
        if (task.cancel()) {
            audit("TASK_CANCELED", taskId, task.fromAgent(), task.toAgent(), null, "CANCELED", 0, true, null, null);
        }
        return task;
    }

    /**
     * INPUT_REQUIRED 태스크에 입력을 제공한다.
     */
    public AcpTask provideInput(String taskId, AcpMessage input) {
        AcpTask task = tasks.get(taskId);
        if (task == null) return null;
        if (task.provideInput(input)) {
            audit("TASK_INPUT_PROVIDED", taskId, task.fromAgent(), task.toAgent(), null, "WORKING", 0, true, null, null);
        }
        return task;
    }

    /**
     * 특정 에이전트와 관련된 태스크 목록을 조회한다.
     */
    public List<AcpTask> listTasks(String agentId, String state, int limit) {
        List<AcpTask> result = new ArrayList<>();
        for (AcpTask task : tasks.values()) {
            if (agentId != null && !agentId.equals(task.fromAgent()) && !agentId.equals(task.toAgent())) continue;
            if (state != null && !state.equalsIgnoreCase(task.state().name())) continue;
            result.add(task);
            if (result.size() >= limit) break;
        }
        result.sort(Comparator.comparing(AcpTask::updatedAt).reversed());
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /** 전체 태스크 수. */
    public int size() { return tasks.size(); }

    /** 통계. */
    public TaskStats stats() {
        long active = tasks.values().stream().filter(t -> !t.state().isTerminal()).count();
        return new TaskStats(totalCreated.get(), totalCompleted.get(), totalFailed.get(), active, tasks.size());
    }

    /** 감사 로그 조회. */
    public List<AcpAuditEntry> recentAudit(int limit) {
        List<AcpAuditEntry> result = new ArrayList<>();
        int count = 0;
        for (AcpAuditEntry entry : auditLog) {
            if (count >= limit) break;
            result.add(entry);
            count++;
        }
        return result;
    }

    private void audit(String eventType, String taskId, String fromAgent, String toAgent,
                       String capability, String state, long durationMs, boolean success,
                       String errorMsg, String remoteAddr) {
        AcpAuditEntry entry = new AcpAuditEntry(
                Instant.now(), eventType, taskId, fromAgent, toAgent,
                capability, state, durationMs, success, errorMsg, remoteAddr);
        auditLog.addFirst(entry);
        while (auditLog.size() > MAX_AUDIT) auditLog.pollLast();
        if (auditLogger.isInfoEnabled()) {
            auditLogger.info(entry.toJson());
        }
    }

    /** 라우팅/메시지 이벤트를 외부에서 기록한다. */
    public void auditEvent(String eventType, String taskId, String fromAgent, String toAgent,
                           String capability, String state, long durationMs, boolean success,
                           String errorMsg, String remoteAddr) {
        audit(eventType, taskId, fromAgent, toAgent, capability, state, durationMs, success, errorMsg, remoteAddr);
    }

    private void evictOldTasks() {
        if (tasks.size() <= MAX_TASKS) return;
        tasks.values().stream()
                .filter(t -> t.state().isTerminal())
                .sorted(Comparator.comparing(AcpTask::updatedAt))
                .limit(tasks.size() - MAX_TASKS + 100)
                .forEach(t -> tasks.remove(t.taskId()));
    }

    public record TaskStats(long totalCreated, long totalCompleted, long totalFailed, long activeCount, int totalStored) {
        public String toJson() {
            return "{\"totalCreated\":" + totalCreated
                    + ",\"totalCompleted\":" + totalCompleted
                    + ",\"totalFailed\":" + totalFailed
                    + ",\"activeCount\":" + activeCount
                    + ",\"totalStored\":" + totalStored + "}";
        }
    }
}
