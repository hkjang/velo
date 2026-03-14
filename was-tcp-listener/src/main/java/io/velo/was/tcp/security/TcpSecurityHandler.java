package io.velo.was.tcp.security;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs IP-based access control on incoming TCP connections.
 * Checks the remote address against allow/deny CIDR lists.
 * <p>
 * Rules:
 * <ul>
 *     <li>If allow list is non-empty: only allow listed CIDRs</li>
 *     <li>If deny list is non-empty: block listed CIDRs</li>
 *     <li>Deny rules take precedence over allow rules</li>
 * </ul>
 */
@ChannelHandler.Sharable
public class TcpSecurityHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpSecurityHandler.class);

    private final List<CidrRange> allowedRanges;
    private final List<CidrRange> deniedRanges;

    public TcpSecurityHandler(List<String> allowedCidrs, List<String> deniedCidrs) {
        this.allowedRanges = parseCidrs(allowedCidrs);
        this.deniedRanges = parseCidrs(deniedCidrs);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        SocketAddress remoteAddr = ctx.channel().remoteAddress();
        if (remoteAddr instanceof InetSocketAddress inetAddr) {
            InetAddress address = inetAddr.getAddress();

            // Check deny list first (deny takes precedence)
            if (!deniedRanges.isEmpty() && matchesAny(address, deniedRanges)) {
                log.warn("TCP connection denied by ACL: {}", address.getHostAddress());
                ctx.close();
                return;
            }

            // Check allow list
            if (!allowedRanges.isEmpty() && !matchesAny(address, allowedRanges)) {
                log.warn("TCP connection not in allow list: {}", address.getHostAddress());
                ctx.close();
                return;
            }
        }
        super.channelActive(ctx);
    }

    private boolean matchesAny(InetAddress address, List<CidrRange> ranges) {
        for (CidrRange range : ranges) {
            if (range.contains(address)) return true;
        }
        return false;
    }

    private static List<CidrRange> parseCidrs(List<String> cidrs) {
        if (cidrs == null) return List.of();
        List<CidrRange> ranges = new ArrayList<>();
        for (String cidr : cidrs) {
            try {
                ranges.add(CidrRange.parse(cidr));
            } catch (Exception e) {
                log.warn("Invalid CIDR format: {}", cidr);
            }
        }
        return List.copyOf(ranges);
    }

    /**
     * Simple CIDR range for IP matching.
     */
    static class CidrRange {
        private final byte[] networkAddress;
        private final int prefixLength;

        CidrRange(byte[] networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String cidr) throws Exception {
            String[] parts = cidr.split("/");
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : (address.getAddress().length * 8);
            return new CidrRange(address.getAddress(), prefix);
        }

        boolean contains(InetAddress address) {
            byte[] addr = address.getAddress();
            if (addr.length != networkAddress.length) return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes && i < addr.length; i++) {
                if (addr[i] != networkAddress[i]) return false;
            }

            if (remainingBits > 0 && fullBytes < addr.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((addr[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) return false;
            }

            return true;
        }
    }
}
