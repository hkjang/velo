package io.velo.was.aiplatform.agp;

import io.velo.was.aiplatform.acp.AcpAgentCard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AGP 에이전트 등록소 — 에이전트 카드를 관리하고 능력/모달리티 기반 검색을 제공한다.
 */
public class AgpAgentRegistry {

    private final ConcurrentHashMap<String, AcpAgentCard> agents = new ConcurrentHashMap<>();

    /** 에이전트를 등록한다. agentId가 없으면 생성한다. */
    public AcpAgentCard register(AcpAgentCard card) {
        String id = card.agentId() != null && !card.agentId().isBlank()
                ? card.agentId()
                : "agent-" + UUID.randomUUID().toString().substring(0, 8);
        AcpAgentCard registered = new AcpAgentCard(
                id, card.name(), card.description(), card.version(), card.provider(),
                card.endpoint(), card.capabilities(), card.modalities(), card.protocols(),
                card.authScheme(), java.time.Instant.now(), card.metadata());
        agents.put(id, registered);
        return registered;
    }

    /** 에이전트를 해제한다. */
    public AcpAgentCard unregister(String agentId) {
        return agents.remove(agentId);
    }

    /** 특정 에이전트를 조회한다. */
    public AcpAgentCard find(String agentId) {
        return agents.get(agentId);
    }

    /** 전체 에이전트 목록. */
    public List<AcpAgentCard> listAgents() {
        return agents.values().stream()
                .sorted(Comparator.comparing(AcpAgentCard::name))
                .collect(Collectors.toList());
    }

    /** 능력 기반 검색. */
    public List<AcpAgentCard> findByCapability(String capability) {
        if (capability == null || capability.isBlank()) return listAgents();
        String lower = capability.toLowerCase(Locale.ROOT);
        return agents.values().stream()
                .filter(a -> a.capabilities() != null
                        && a.capabilities().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(lower)))
                .collect(Collectors.toList());
    }

    /** 모달리티 기반 검색. */
    public List<AcpAgentCard> findByModality(String modality) {
        if (modality == null || modality.isBlank()) return listAgents();
        String lower = modality.toLowerCase(Locale.ROOT);
        return agents.values().stream()
                .filter(a -> a.modalities() != null
                        && a.modalities().stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).equals(lower)))
                .collect(Collectors.toList());
    }

    /** 프로토콜 기반 검색. */
    public List<AcpAgentCard> findByProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) return listAgents();
        String lower = protocol.toLowerCase(Locale.ROOT);
        return agents.values().stream()
                .filter(a -> a.protocols() != null
                        && a.protocols().stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).equals(lower)))
                .collect(Collectors.toList());
    }

    /** 등록된 에이전트 수. */
    public int size() {
        return agents.size();
    }
}
