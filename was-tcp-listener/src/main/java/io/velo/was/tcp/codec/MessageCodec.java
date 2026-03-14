package io.velo.was.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * SPI interface for custom message codecs.
 * Users can implement this to convert between {@link ByteBuf} and {@link TcpMessage}.
 */
public abstract class MessageCodec extends ByteToMessageDecoder {

    /**
     * Decodes a {@link ByteBuf} frame into a {@link TcpMessage}.
     * Called after the framing decoder has extracted a complete frame.
     */
    public abstract TcpMessage decodeMessage(ByteBuf frame);

    /**
     * Encodes a {@link TcpMessage} into a {@link ByteBuf} for sending.
     */
    public abstract ByteBuf encodeMessage(ChannelHandlerContext ctx, TcpMessage message);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() > 0) {
            TcpMessage message = decodeMessage(in);
            if (message != null) {
                out.add(message);
            }
        }
    }
}
