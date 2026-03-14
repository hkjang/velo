package io.velo.was.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public final class HttpResponses {

    private HttpResponses() {
    }

    public static FullHttpResponse jsonOk(String json) {
        return json(HttpResponseStatus.OK, json);
    }

    public static FullHttpResponse notFound(String message) {
        return json(HttpResponseStatus.NOT_FOUND, "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    public static FullHttpResponse methodNotAllowed(String message) {
        return json(HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    public static FullHttpResponse badRequest(String message) {
        return json(HttpResponseStatus.BAD_REQUEST, "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    public static FullHttpResponse serverError(String message) {
        return json(HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    public static FullHttpResponse json(HttpResponseStatus status, String json) {
        ByteBuf body = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        response.headers().set("X-Content-Type-Options", "nosniff");
        response.headers().set("X-Frame-Options", "DENY");
        response.headers().set("Referrer-Policy", "no-referrer");
        return response;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

