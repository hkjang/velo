package io.velo.was.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

/**
 * Default message codec that treats each frame as a simple byte-array message.
 * Message type defaults to "default".
 */
public class DefaultMessageCodec extends MessageCodec {

    @Override
    public TcpMessage decodeMessage(ByteBuf frame) {
        byte[] payload = new byte[frame.readableBytes()];
        frame.readBytes(payload);
        return new TcpMessage("default", payload);
    }

    @Override
    public ByteBuf encodeMessage(ChannelHandlerContext ctx, TcpMessage message) {
        return Unpooled.wrappedBuffer(message.payload());
    }
}
