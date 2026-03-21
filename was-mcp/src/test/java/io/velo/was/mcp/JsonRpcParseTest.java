package io.velo.was.mcp;

import io.velo.was.mcp.protocol.JsonRpcRequest;
import io.velo.was.mcp.protocol.JsonRpcResponse;
import io.velo.was.mcp.protocol.JsonRpcError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcParseTest {

    @Test
    void parseInitialize() {
        String json = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2024-11-05",
                    "clientInfo": { "name": "TestClient", "version": "1.0" }
                  }
                }
                """;
        JsonRpcRequest req = JsonRpcRequest.parse(json);
        assertEquals("2.0", req.jsonrpc());
        assertEquals(1L, ((Number) req.id()).longValue());
        assertEquals("initialize", req.method());
        assertNotNull(req.params());
        assertEquals("2024-11-05", req.params().get("protocolVersion"));
    }

    @Test
    void parseNotification() {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        JsonRpcRequest req = JsonRpcRequest.parse(json);
        assertTrue(req.isNotification());
        assertEquals("notifications/initialized", req.method());
    }

    @Test
    void parseToolsCall() {
        String json = """
                {"jsonrpc":"2.0","id":"abc","method":"tools/call",
                 "params":{"name":"infer","arguments":{"prompt":"Hello","requestType":"CHAT"}}}
                """;
        JsonRpcRequest req = JsonRpcRequest.parse(json);
        assertEquals("abc", req.id());
        assertEquals("tools/call", req.method());
        assertEquals("infer", req.params().get("name"));
    }

    @Test
    void successResponse() {
        String resp = JsonRpcResponse.success(1L, "{\"tools\":[]}");
        assertTrue(resp.contains("\"result\""));
        assertTrue(resp.contains("\"id\":1"));
    }

    @Test
    void errorResponse() {
        String resp = JsonRpcResponse.error(1L, JsonRpcError.methodNotFound("unknown/method"));
        assertTrue(resp.contains("\"error\""));
        assertTrue(resp.contains("-32601"));
    }
}
