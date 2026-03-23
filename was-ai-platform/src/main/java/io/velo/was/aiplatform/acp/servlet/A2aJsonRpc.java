package io.velo.was.aiplatform.acp.servlet;

import io.velo.was.aiplatform.acp.AcpMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A2A JSON-RPC 2.0 파싱/응답 빌더.
 *
 * <p>Google A2A 프로토콜의 JSON-RPC 2.0 요청을 파싱하고,
 * 표준 응답 포맷으로 변환한다.</p>
 */
public final class A2aJsonRpc {

    private static final Pattern JSON_STRING = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_NUMBER = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*(-?\\d+)");

    private A2aJsonRpc() {}

    /** JSON-RPC 2.0 요청에서 method를 추출한다. */
    public static String extractMethod(String body) {
        return extractStr(body, "method");
    }

    /** JSON-RPC 2.0 요청에서 id를 추출한다. */
    public static String extractId(String body) {
        // id는 문자열 또는 숫자일 수 있다
        String strId = extractStr(body, "id");
        if (!strId.isBlank()) return "\"" + AcpMessage.esc(strId) + "\"";
        String numId = extractNum(body, "id");
        return numId.isBlank() ? "null" : numId;
    }

    /** params 블록에서 텍스트 필드를 추출한다. */
    public static String extractParam(String body, String field) {
        return extractStr(body, field);
    }

    /** A2A 메시지의 parts에서 텍스트를 추출한다. */
    public static String extractTextFromParts(String body) {
        // "text":"..." 중 parts 내부 것을 추출
        Pattern p = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
        Matcher m = p.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = m.group(1);
            // method/type 필드는 제외
            if (!val.equals("tasks/send") && !val.equals("tasks/sendSubscribe")
                    && !val.equals("tasks/get") && !val.equals("tasks/cancel")
                    && !val.equals("message/send")) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(val.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"));
            }
        }
        return sb.toString();
    }

    /** A2A 메시지를 AcpMessage로 변환한다. */
    public static AcpMessage toAcpMessage(String body) {
        String role = extractStr(body, "role");
        if (role.isBlank()) role = "user";
        String text = extractTextFromParts(body);
        if (text.isBlank()) {
            text = extractStr(body, "text");
        }
        return AcpMessage.text(role, text);
    }

    // ── JSON-RPC 2.0 응답 빌더 ──

    /** 성공 응답. */
    public static String success(String id, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}";
    }

    /** 에러 응답. */
    public static String error(String id, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"error\":{\"code\":" + code + ",\"message\":\"" + AcpMessage.esc(message) + "\"}}";
    }

    /** 메서드를 찾을 수 없음 (-32601). */
    public static String methodNotFound(String id, String method) {
        return error(id, -32601, "Method not found: " + method);
    }

    /** 파싱 에러 (-32700). */
    public static String parseError() {
        return error("null", -32700, "Parse error");
    }

    /** 유효하지 않은 요청 (-32600). */
    public static String invalidRequest(String id) {
        return error(id, -32600, "Invalid Request");
    }

    private static String extractStr(String body, String field) {
        Matcher m = JSON_STRING.matcher(body == null ? "" : body);
        while (m.find()) { if (field.equals(m.group(1))) return m.group(2); }
        return "";
    }

    private static String extractNum(String body, String field) {
        Matcher m = JSON_NUMBER.matcher(body == null ? "" : body);
        while (m.find()) { if (field.equals(m.group(1))) return m.group(2); }
        return "";
    }
}
