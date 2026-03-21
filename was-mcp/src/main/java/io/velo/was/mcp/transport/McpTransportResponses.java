package io.velo.was.mcp.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/** Response helpers shared by MCP transport handlers. */
final class McpTransportResponses {

    private McpTransportResponses() {}

    /** JSON-RPC response with optional session ID header. */
    static FullHttpResponse jsonRpc(String json, String sessionId) {
        ByteBuf body = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body);
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())
                .set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE)
                .set("X-Content-Type-Options", "nosniff");
        if (sessionId != null) {
            response.headers().set(McpPostHandler.SESSION_HEADER, sessionId);
        }
        return response;
    }

    /** 204 No Content for notifications. */
    static FullHttpResponse noContent(String sessionId) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        if (sessionId != null) {
            response.headers().set(McpPostHandler.SESSION_HEADER, sessionId);
        }
        return response;
    }
}
