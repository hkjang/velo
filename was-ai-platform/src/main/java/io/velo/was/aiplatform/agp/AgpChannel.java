package io.velo.was.aiplatform.agp;

import io.velo.was.aiplatform.acp.AcpMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AGP 에이전트 간 통신 채널.
 * 두 에이전트 사이의 메시지 교환 이력을 유지한다.
 */
public class AgpChannel {

    private static final int MAX_HISTORY = 100;

    private final String channelId;
    private final String agentA;
    private final String agentB;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;
    private final CopyOnWriteArrayList<AcpMessage> messageHistory = new CopyOnWriteArrayList<>();

    public AgpChannel(String agentA, String agentB) {
        this.channelId = "ch-" + UUID.randomUUID().toString().substring(0, 8);
        this.agentA = agentA;
        this.agentB = agentB;
        this.createdAt = Instant.now();
        this.lastActivityAt = this.createdAt;
    }

    public String channelId() { return channelId; }
    public String agentA() { return agentA; }
    public String agentB() { return agentB; }
    public Instant createdAt() { return createdAt; }
    public Instant lastActivityAt() { return lastActivityAt; }
    public List<AcpMessage> history() { return List.copyOf(messageHistory); }
    public int messageCount() { return messageHistory.size(); }

    /** 채널에 해당 에이전트가 참여하는지 확인. */
    public boolean involves(String agentId) {
        return agentA.equals(agentId) || agentB.equals(agentId);
    }

    /** 메시지를 채널에 추가. */
    public void addMessage(AcpMessage message) {
        messageHistory.add(message);
        lastActivityAt = Instant.now();
        while (messageHistory.size() > MAX_HISTORY) {
            messageHistory.remove(0);
        }
    }

    /** JSON 직렬화. */
    public String toJson() {
        return "{\"channelId\":\"" + AcpMessage.esc(channelId) + "\""
                + ",\"agentA\":\"" + AcpMessage.esc(agentA) + "\""
                + ",\"agentB\":\"" + AcpMessage.esc(agentB) + "\""
                + ",\"messageCount\":" + messageHistory.size()
                + ",\"createdAt\":\"" + createdAt + "\""
                + ",\"lastActivityAt\":\"" + lastActivityAt + "\""
                + "}";
    }
}
