package io.velo.was.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty-backed implementation of {@link SseSink}.
 *
 * <p>Writes chunked HTTP/1.1 SSE frames directly to the channel.
 * The initial response headers are sent in the constructor.
 * All public methods are thread-safe.
 */
final class NettySseSink implements SseSink {

    private final ChannelHandlerContext ctx;
    private final AtomicBoolean open = new AtomicBoolean(true);

    NettySseSink(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        sendHeaders();
    }

    // ── SseSink ─────────────────────────────────────────────────────────────

    @Override
    public void emit(String data) {
        emit(null, data);
    }

    @Override
    public void emit(String eventType, String data) {
        if (!isOpen()) return;
        StringBuilder frame = new StringBuilder(256);
        if (eventType != null && !eventType.isBlank()) {
            frame.append("event: ").append(eventType).append('\n');
        }
        // Each line of data must be prefixed with "data: "
        if (data != null) {
            for (String line : data.split("\n", -1)) {
                frame.append("data: ").append(line).append('\n');
            }
        }
        frame.append('\n'); // blank line terminates the event
        writeChunk(frame.toString());
    }

    @Override
    public void ping() {
        if (!isOpen()) return;
        writeChunk(": ping\n\n");
    }

    @Override
    public boolean isOpen() {
        return open.get() && ctx.channel().isActive();
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private void sendHeaders() {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=UTF-8")
                .set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
                .set("X-Accel-Buffering", "no"); // disable nginx proxy buffering
        ctx.writeAndFlush(response);
    }

    private void writeChunk(String text) {
        ctx.writeAndFlush(new DefaultHttpContent(
                Unpooled.copiedBuffer(text, CharsetUtil.UTF_8)));
    }
}
