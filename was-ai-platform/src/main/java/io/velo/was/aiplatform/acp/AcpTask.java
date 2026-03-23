package io.velo.was.aiplatform.acp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ACP 태스크 — 에이전트 간 작업 단위.
 * 상태 머신: SUBMITTED → WORKING → COMPLETED | FAILED | CANCELED | INPUT_REQUIRED
 */
public class AcpTask {

    /** 태스크 상태. */
    public enum State {
        SUBMITTED, WORKING, INPUT_REQUIRED, COMPLETED, FAILED, CANCELED;

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELED;
        }
    }

    /** 태스크 아티팩트 — 결과물 (파일, 데이터 등). */
    public record Artifact(String name, String contentType, String text, String dataBase64, Instant createdAt) {
        public String toJson() {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"name\":\"").append(AcpMessage.esc(name)).append("\"");
            sb.append(",\"contentType\":\"").append(AcpMessage.esc(contentType)).append("\"");
            if (text != null) sb.append(",\"text\":\"").append(AcpMessage.esc(text)).append("\"");
            if (dataBase64 != null) sb.append(",\"dataBase64\":\"").append(AcpMessage.esc(dataBase64)).append("\"");
            sb.append(",\"createdAt\":\"").append(createdAt).append("\"}");
            return sb.toString();
        }
    }

    private final String taskId;
    private final String fromAgent;
    private final String toAgent;
    private volatile State state;
    private final AcpMessage input;
    private volatile AcpMessage output;
    private final CopyOnWriteArrayList<Artifact> artifacts = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AcpMessage> history = new CopyOnWriteArrayList<>();
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private final Map<String, String> metadata;

    public AcpTask(String taskId, String fromAgent, String toAgent, AcpMessage input, Map<String, String> metadata) {
        this.taskId = taskId;
        this.fromAgent = fromAgent;
        this.toAgent = toAgent;
        this.state = State.SUBMITTED;
        this.input = input;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.metadata = metadata != null ? metadata : Map.of();
        this.history.add(input);
    }

    // ── Getters ──

    public String taskId() { return taskId; }
    public String fromAgent() { return fromAgent; }
    public String toAgent() { return toAgent; }
    public State state() { return state; }
    public AcpMessage input() { return input; }
    public AcpMessage output() { return output; }
    public List<Artifact> artifacts() { return List.copyOf(artifacts); }
    public List<AcpMessage> history() { return List.copyOf(history); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Map<String, String> metadata() { return metadata; }

    // ── State transitions ──

    /** SUBMITTED → WORKING */
    public synchronized boolean start() {
        if (state != State.SUBMITTED) return false;
        state = State.WORKING;
        updatedAt = Instant.now();
        return true;
    }

    /** WORKING → COMPLETED with output */
    public synchronized boolean complete(AcpMessage output) {
        if (state != State.WORKING && state != State.INPUT_REQUIRED) return false;
        this.output = output;
        this.state = State.COMPLETED;
        this.updatedAt = Instant.now();
        if (output != null) history.add(output);
        return true;
    }

    /** WORKING → FAILED */
    public synchronized boolean fail(String errorMsg) {
        if (state.isTerminal()) return false;
        this.output = AcpMessage.text("system", "Error: " + errorMsg);
        this.state = State.FAILED;
        this.updatedAt = Instant.now();
        return true;
    }

    /** Any non-terminal → CANCELED */
    public synchronized boolean cancel() {
        if (state.isTerminal()) return false;
        this.state = State.CANCELED;
        this.updatedAt = Instant.now();
        return true;
    }

    /** WORKING → INPUT_REQUIRED */
    public synchronized boolean requestInput(AcpMessage prompt) {
        if (state != State.WORKING) return false;
        this.state = State.INPUT_REQUIRED;
        this.updatedAt = Instant.now();
        if (prompt != null) history.add(prompt);
        return true;
    }

    /** INPUT_REQUIRED → WORKING (with additional input) */
    public synchronized boolean provideInput(AcpMessage additionalInput) {
        if (state != State.INPUT_REQUIRED) return false;
        this.state = State.WORKING;
        this.updatedAt = Instant.now();
        if (additionalInput != null) history.add(additionalInput);
        return true;
    }

    /** 아티팩트 추가. */
    public void addArtifact(Artifact artifact) {
        artifacts.add(artifact);
        updatedAt = Instant.now();
    }

    /** 메시지 추가 (중간 진행 상황). */
    public void addMessage(AcpMessage message) {
        history.add(message);
        updatedAt = Instant.now();
    }

    /** JSON 직렬화. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"taskId\":\"").append(AcpMessage.esc(taskId)).append("\"");
        sb.append(",\"fromAgent\":\"").append(AcpMessage.esc(fromAgent)).append("\"");
        sb.append(",\"toAgent\":\"").append(AcpMessage.esc(toAgent)).append("\"");
        sb.append(",\"state\":\"").append(state.name()).append("\"");
        sb.append(",\"input\":").append(input != null ? input.toJson() : "null");
        sb.append(",\"output\":").append(output != null ? output.toJson() : "null");
        sb.append(",\"artifacts\":[");
        for (int i = 0; i < artifacts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(artifacts.get(i).toJson());
        }
        sb.append("],\"historySize\":").append(history.size());
        sb.append(",\"createdAt\":\"").append(createdAt).append("\"");
        sb.append(",\"updatedAt\":\"").append(updatedAt).append("\"");
        sb.append(",\"metadata\":{");
        boolean first = true;
        for (var entry : metadata.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("\"").append(AcpMessage.esc(entry.getKey())).append("\":\"").append(AcpMessage.esc(entry.getValue())).append("\"");
        }
        sb.append("}}");
        return sb.toString();
    }

    /** 간략 JSON (목록용). */
    public String toSummaryJson() {
        return "{\"taskId\":\"" + AcpMessage.esc(taskId) + "\""
                + ",\"fromAgent\":\"" + AcpMessage.esc(fromAgent) + "\""
                + ",\"toAgent\":\"" + AcpMessage.esc(toAgent) + "\""
                + ",\"state\":\"" + state.name() + "\""
                + ",\"createdAt\":\"" + createdAt + "\""
                + ",\"updatedAt\":\"" + updatedAt + "\""
                + "}";
    }
}
