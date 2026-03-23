package io.velo.was.aiplatform.agp;

import io.velo.was.aiplatform.acp.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AGP 중앙 게이트웨이 — 에이전트 등록, 발견, 라우팅, 메시지 중개, 채널 관리.
 * MCP(도구), ACP(에이전트 간), AGP(허브)를 통합하는 오케스트레이터.
 */
public class AgpGateway {

    private final AgpAgentRegistry agentRegistry;
    private final AgpRouteTable routeTable;
    private final AcpTaskManager taskManager;
    private final ConcurrentHashMap<String, AgpChannel> channels = new ConcurrentHashMap<>();
    private final AtomicLong routedTasks = new AtomicLong();
    private final AtomicLong forwardedMessages = new AtomicLong();

    public AgpGateway(AgpAgentRegistry agentRegistry, AgpRouteTable routeTable, AcpTaskManager taskManager) {
        this.agentRegistry = agentRegistry;
        this.routeTable = routeTable;
        this.taskManager = taskManager;
    }

    // ── 에이전트 관리 (delegate) ──

    public AgpAgentRegistry agentRegistry() { return agentRegistry; }
    public AgpRouteTable routeTable() { return routeTable; }
    public AcpTaskManager taskManager() { return taskManager; }

    // ── 태스크 라우팅 ──

    /**
     * 태스크를 적합한 에이전트로 라우팅한다.
     * toAgent가 지정되면 직접 라우팅, 아니면 능력 기반으로 최적 에이전트를 선택한다.
     */
    public AcpTask routeTask(String fromAgent, String toAgent, AcpMessage input,
                             String capability, Map<String, String> metadata) {
        // 대상 에이전트 결정
        String resolvedTarget = toAgent;
        if (resolvedTarget == null || resolvedTarget.isBlank()) {
            if (capability != null && !capability.isBlank()) {
                List<AcpAgentCard> candidates = routeTable.resolve(capability);
                if (!candidates.isEmpty()) {
                    resolvedTarget = candidates.get(0).agentId();
                }
            }
            if (resolvedTarget == null || resolvedTarget.isBlank()) {
                // 의도 텍스트에서 자동 탐색
                AcpAgentCard matched = routeTable.resolveByIntent(input.textContent());
                if (matched != null) {
                    resolvedTarget = matched.agentId();
                }
            }
        }

        if (resolvedTarget == null || resolvedTarget.isBlank()) {
            resolvedTarget = "unresolved";
        }

        AcpTask task = taskManager.createTask(fromAgent, resolvedTarget, input, metadata);
        routedTasks.incrementAndGet();
        taskManager.auditEvent("ROUTE_RESOLVED", task.taskId(), fromAgent, resolvedTarget,
                capability, "SUBMITTED", 0, true, null, null);

        // 자동으로 WORKING 상태로 전환
        taskManager.startTask(task.taskId());
        return task;
    }

    // ── 메시지 전달 ──

    /**
     * 에이전트 간 메시지를 직접 전달한다. 채널을 자동 생성하거나 기존 채널을 재사용한다.
     */
    public AgpChannel forwardMessage(String fromAgent, String toAgent, AcpMessage message) {
        String channelKey = channelKey(fromAgent, toAgent);
        AgpChannel channel = channels.computeIfAbsent(channelKey, k -> new AgpChannel(fromAgent, toAgent));
        channel.addMessage(message);
        forwardedMessages.incrementAndGet();
        taskManager.auditEvent("MESSAGE_FORWARDED", null, fromAgent, toAgent,
                null, null, 0, true, null, null);
        return channel;
    }

    /**
     * 능력 기반 브로드캐스트 — 해당 능력을 가진 모든 에이전트에 메시지를 전달한다.
     */
    public List<AgpChannel> broadcast(String fromAgent, String capability, AcpMessage message) {
        List<AcpAgentCard> targets = routeTable.resolve(capability);
        List<AgpChannel> result = new ArrayList<>();
        for (AcpAgentCard target : targets) {
            if (!target.agentId().equals(fromAgent)) {
                result.add(forwardMessage(fromAgent, target.agentId(), message));
            }
        }
        return result;
    }

    // ── 채널 관리 ──

    /** 특정 에이전트의 활성 채널 목록. */
    public List<AgpChannel> getChannels(String agentId) {
        if (agentId == null) return List.copyOf(channels.values());
        return channels.values().stream()
                .filter(ch -> ch.involves(agentId))
                .toList();
    }

    /** 전체 채널 목록. */
    public List<AgpChannel> allChannels() {
        return List.copyOf(channels.values());
    }

    /** 채널 수. */
    public int channelCount() { return channels.size(); }

    // ── 통계 ──

    /** 게이트웨이 통계. */
    public GatewayStats stats() {
        AcpTaskManager.TaskStats taskStats = taskManager.stats();
        return new GatewayStats(
                agentRegistry.size(),
                channels.size(),
                routeTable.size(),
                routedTasks.get(),
                forwardedMessages.get(),
                routeTable.resolveCount(),
                routeTable.resolveHits(),
                taskStats
        );
    }

    public record GatewayStats(
            int registeredAgents, int activeChannels, int routeRules,
            long routedTasks, long forwardedMessages,
            long resolveCount, long resolveHits,
            AcpTaskManager.TaskStats taskStats
    ) {
        public String toJson() {
            return "{\"registeredAgents\":" + registeredAgents
                    + ",\"activeChannels\":" + activeChannels
                    + ",\"routeRules\":" + routeRules
                    + ",\"routedTasks\":" + routedTasks
                    + ",\"forwardedMessages\":" + forwardedMessages
                    + ",\"resolveCount\":" + resolveCount
                    + ",\"resolveHits\":" + resolveHits
                    + ",\"tasks\":" + taskStats.toJson()
                    + "}";
        }
    }

    private static String channelKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "::" + b : b + "::" + a;
    }
}
