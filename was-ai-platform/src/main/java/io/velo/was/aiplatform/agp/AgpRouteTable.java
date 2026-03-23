package io.velo.was.aiplatform.agp;

import io.velo.was.aiplatform.acp.AcpAgentCard;
import io.velo.was.aiplatform.acp.AcpMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AGP 의도 기반 라우팅 테이블.
 * 능력(capability) → 에이전트 매핑으로 요청을 적합한 에이전트에 라우팅한다.
 */
public class AgpRouteTable {

    /**
     * 라우팅 규칙.
     */
    public record RouteRule(String capability, String agentId, int priority, int weight,
                            java.time.Instant createdAt) {
        public String toJson() {
            return "{\"capability\":\"" + AcpMessage.esc(capability) + "\""
                    + ",\"agentId\":\"" + AcpMessage.esc(agentId) + "\""
                    + ",\"priority\":" + priority
                    + ",\"weight\":" + weight
                    + ",\"createdAt\":\"" + createdAt + "\""
                    + "}";
        }
    }

    private final ConcurrentHashMap<String, List<RouteRule>> routes = new ConcurrentHashMap<>();
    private final AtomicLong resolveCount = new AtomicLong();
    private final AtomicLong resolveHits = new AtomicLong();
    private final AgpAgentRegistry agentRegistry;

    public AgpRouteTable(AgpAgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    /** 라우팅 규칙 추가. */
    public RouteRule addRoute(String capability, String agentId, int priority, int weight) {
        RouteRule rule = new RouteRule(capability.toLowerCase(Locale.ROOT), agentId, priority, weight, java.time.Instant.now());
        routes.computeIfAbsent(capability.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(rule);
        return rule;
    }

    /** 라우팅 규칙 삭제. */
    public boolean removeRoute(String capability, String agentId) {
        String key = capability.toLowerCase(Locale.ROOT);
        List<RouteRule> rules = routes.get(key);
        if (rules == null) return false;
        boolean removed = rules.removeIf(r -> r.agentId().equals(agentId));
        if (rules.isEmpty()) routes.remove(key);
        return removed;
    }

    /** 능력 기반 에이전트 조회 (우선순위+가중치 정렬). */
    public List<AcpAgentCard> resolve(String capability) {
        resolveCount.incrementAndGet();
        String key = capability.toLowerCase(Locale.ROOT);
        List<RouteRule> rules = routes.get(key);
        if (rules == null || rules.isEmpty()) {
            // 라우팅 규칙이 없으면 레지스트리에서 능력 기반 검색
            List<AcpAgentCard> fallback = agentRegistry.findByCapability(capability);
            if (!fallback.isEmpty()) resolveHits.incrementAndGet();
            return fallback;
        }
        resolveHits.incrementAndGet();
        return rules.stream()
                .sorted(Comparator.comparingInt(RouteRule::priority).reversed()
                        .thenComparingInt(RouteRule::weight).reversed())
                .map(r -> agentRegistry.find(r.agentId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 의도 텍스트에서 키워드 매칭으로 최적 에이전트를 찾는다. */
    public AcpAgentCard resolveByIntent(String intentText) {
        if (intentText == null || intentText.isBlank()) return null;
        String lowered = intentText.toLowerCase(Locale.ROOT);

        // 라우팅 테이블의 모든 능력과 의도 텍스트를 매칭
        AcpAgentCard bestMatch = null;
        int bestPriority = -1;
        for (var entry : routes.entrySet()) {
            if (lowered.contains(entry.getKey())) {
                for (RouteRule rule : entry.getValue()) {
                    if (rule.priority() > bestPriority) {
                        AcpAgentCard agent = agentRegistry.find(rule.agentId());
                        if (agent != null) {
                            bestMatch = agent;
                            bestPriority = rule.priority();
                        }
                    }
                }
            }
        }

        // 라우팅 테이블에 없으면 레지스트리 능력 키워드 직접 매칭
        if (bestMatch == null) {
            for (AcpAgentCard agent : agentRegistry.listAgents()) {
                if (agent.capabilities() != null) {
                    for (String cap : agent.capabilities()) {
                        if (lowered.contains(cap.toLowerCase(Locale.ROOT))) {
                            return agent;
                        }
                    }
                }
            }
        }
        return bestMatch;
    }

    /** 전체 라우팅 규칙 조회. */
    public List<RouteRule> allRoutes() {
        return routes.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparing(RouteRule::capability).thenComparingInt(RouteRule::priority).reversed())
                .toList();
    }

    /** 라우팅 규칙 수. */
    public int size() {
        return (int) routes.values().stream().mapToLong(List::size).sum();
    }

    /** 해결 통계. */
    public long resolveCount() { return resolveCount.get(); }
    public long resolveHits() { return resolveHits.get(); }
}
