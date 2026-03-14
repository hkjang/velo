package io.velo.was.servlet;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.servlet.http.Cookie;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ServletResponseContext {

    private final ServletBodyOutputStream outputStream = new ServletBodyOutputStream();
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private final List<Cookie> cookies = new ArrayList<>();
    private int status = 200;
    private String characterEncoding = StandardCharsets.UTF_8.name();
    private String contentType = "text/plain; charset=UTF-8";
    private boolean committed;
    private PrintWriter writer = newWriter();

    public ServletBodyOutputStream outputStream() {
        return outputStream;
    }

    public PrintWriter writer() {
        return writer;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }

    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }

    public void setHeader(String name, String value) {
        headers.put(name, new ArrayList<>(List.of(value)));
    }

    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public String characterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        if (committed || characterEncoding == null || characterEncoding.isBlank()) {
            return;
        }
        this.characterEncoding = characterEncoding;
    }

    public String contentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        if (committed || contentType == null || contentType.isBlank()) {
            return;
        }
        this.contentType = contentType;
    }

    public void reset() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        writer.flush();
        outputStream.reset();
        writer = newWriter();
        headers.clear();
        cookies.clear();
        status = 200;
    }

    public boolean isCommitted() {
        return committed;
    }

    public FullHttpResponse toNettyResponse(boolean headRequest, boolean newSession, String sessionId) {
        writer.flush();
        byte[] body = outputStream.toByteArray();
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(status),
                headRequest ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body));

        if (contentType != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            response.headers().set(entry.getKey(), entry.getValue());
        }
        for (Cookie cookie : cookies) {
            response.headers().add(HttpHeaderNames.SET_COOKIE, cookie.getName() + "=" + cookie.getValue() + "; Path=/; HttpOnly");
        }
        if (newSession && sessionId != null) {
            response.headers().add(HttpHeaderNames.SET_COOKIE, "JSESSIONID=" + sessionId + "; Path=/; HttpOnly");
        }
        committed = true;
        return response;
    }

    private PrintWriter newWriter() {
        return new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
    }
}
