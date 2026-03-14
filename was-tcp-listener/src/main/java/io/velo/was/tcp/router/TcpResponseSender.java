package io.velo.was.tcp.router;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.velo.was.tcp.codec.TcpMessage;

/**
 * Provides methods for sending responses back to the TCP client.
 * Supports synchronous reply, async reply, and fire-and-forget patterns.
 */
public class TcpResponseSender {

    private final ChannelHandlerContext ctx;

    public TcpResponseSender(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Sends a synchronous reply and flushes immediately.
     */
    public void reply(byte[] payload) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(payload));
    }

    /**
     * Sends a reply with a length-prefixed header (4 bytes big-endian).
     */
    public void replyWithLengthHeader(byte[] payload) {
        var buf = ctx.alloc().buffer(4 + payload.length);
        buf.writeInt(payload.length);
        buf.writeBytes(payload);
        ctx.writeAndFlush(buf);
    }

    /**
     * Sends a reply as a {@link TcpMessage} (for codec-encoded responses).
     */
    public void reply(TcpMessage message) {
        reply(message.payload());
    }

    /**
     * Writes a response without flushing (for batching).
     */
    public void write(byte[] payload) {
        ctx.write(Unpooled.wrappedBuffer(payload));
    }

    /**
     * Flushes any pending writes.
     */
    public void flush() {
        ctx.flush();
    }

    /**
     * Closes the connection after sending a final response.
     */
    public void replyAndClose(byte[] payload) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(payload))
                .addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }

    /**
     * Closes the connection.
     */
    public void close() {
        ctx.close();
    }

    /**
     * Returns the underlying channel context.
     */
    public ChannelHandlerContext context() {
        return ctx;
    }
}
