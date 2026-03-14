package io.velo.was.tcp.security;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter that enforces:
 * <ul>
 *     <li>Maximum total concurrent connections</li>
 *     <li>Maximum connections per IP address</li>
 * </ul>
 */
@ChannelHandler.Sharable
public class TcpRateLimiter extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpRateLimiter.class);

    private final int maxConnections;
    private final int perIpLimit;
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final Map<String, AtomicInteger> perIpConnections = new ConcurrentHashMap<>();

    public TcpRateLimiter(int maxConnections, int perIpLimit) {
        this.maxConnections = maxConnections;
        this.perIpLimit = perIpLimit;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int total = totalConnections.incrementAndGet();
        if (total > maxConnections) {
            totalConnections.decrementAndGet();
            log.warn("Max connections exceeded ({}/{}), rejecting: {}",
                    total, maxConnections, ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        String ip = extractIp(ctx);
        if (ip != null && perIpLimit > 0) {
            AtomicInteger ipCount = perIpConnections.computeIfAbsent(ip, k -> new AtomicInteger(0));
            int count = ipCount.incrementAndGet();
            if (count > perIpLimit) {
                ipCount.decrementAndGet();
                totalConnections.decrementAndGet();
                log.warn("Per-IP connection limit exceeded ({}/{}) for IP: {}",
                        count, perIpLimit, ip);
                ctx.close();
                return;
            }
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        totalConnections.decrementAndGet();

        String ip = extractIp(ctx);
        if (ip != null) {
            AtomicInteger ipCount = perIpConnections.get(ip);
            if (ipCount != null) {
                int remaining = ipCount.decrementAndGet();
                if (remaining <= 0) {
                    perIpConnections.remove(ip);
                }
            }
        }

        super.channelInactive(ctx);
    }

    private String extractIp(ChannelHandlerContext ctx) {
        SocketAddress addr = ctx.channel().remoteAddress();
        if (addr instanceof InetSocketAddress inetAddr) {
            InetAddress address = inetAddr.getAddress();
            return address != null ? address.getHostAddress() : null;
        }
        return null;
    }

    public int currentTotal() { return totalConnections.get(); }
    public int currentForIp(String ip) {
        AtomicInteger count = perIpConnections.get(ip);
        return count != null ? count.get() : 0;
    }
}
