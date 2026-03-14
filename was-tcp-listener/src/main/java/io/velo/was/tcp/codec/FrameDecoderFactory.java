package io.velo.was.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.velo.was.config.ServerConfiguration.FramingConfig;
import io.velo.was.config.ServerConfiguration.FramingType;

import java.nio.charset.StandardCharsets;

/**
 * Factory for creating frame decoders based on {@link FramingType} configuration.
 */
public final class FrameDecoderFactory {

    private FrameDecoderFactory() {}

    /**
     * Creates the appropriate Netty frame decoder for the given framing type and config.
     */
    public static ChannelHandler createDecoder(FramingType type, FramingConfig config) {
        return switch (type) {
            case RAW -> null; // No framing, raw ByteBuf pass-through
            case LINE -> new LineBasedFrameDecoder(config.getMaxFrameLength());
            case FIXED_LENGTH -> new FixedLengthFrameDecoder(config.getLength());
            case DELIMITER -> {
                byte[] delimBytes = config.getDelimiter().getBytes(StandardCharsets.UTF_8);
                ByteBuf delimiter = Unpooled.wrappedBuffer(delimBytes);
                yield new DelimiterBasedFrameDecoder(config.getMaxFrameLength(), delimiter);
            }
            case LENGTH_FIELD -> new LengthFieldBasedFrameDecoder(
                    config.getMaxFrameLength(),
                    config.getLengthFieldOffset(),
                    config.getLengthFieldLength(),
                    0, // length adjustment
                    config.getLengthFieldLength() // initial bytes to strip (strip the length field itself)
            );
        };
    }
}
