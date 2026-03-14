package io.velo.was.tcp.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TcpSecurityHandlerTest {

    @Test
    void cidrContainsSameAddress() throws Exception {
        TcpSecurityHandler.CidrRange range = TcpSecurityHandler.CidrRange.parse("192.168.1.0/24");
        assertTrue(range.contains(InetAddress.getByName("192.168.1.1")));
        assertTrue(range.contains(InetAddress.getByName("192.168.1.254")));
        assertFalse(range.contains(InetAddress.getByName("192.168.2.1")));
    }

    @Test
    void cidrSingleHost() throws Exception {
        TcpSecurityHandler.CidrRange range = TcpSecurityHandler.CidrRange.parse("10.0.0.5/32");
        assertTrue(range.contains(InetAddress.getByName("10.0.0.5")));
        assertFalse(range.contains(InetAddress.getByName("10.0.0.6")));
    }

    @Test
    void cidrBroadRange() throws Exception {
        TcpSecurityHandler.CidrRange range = TcpSecurityHandler.CidrRange.parse("10.0.0.0/8");
        assertTrue(range.contains(InetAddress.getByName("10.255.255.255")));
        assertTrue(range.contains(InetAddress.getByName("10.0.0.1")));
        assertFalse(range.contains(InetAddress.getByName("11.0.0.1")));
    }

    @Test
    void cidrWithoutPrefix() throws Exception {
        TcpSecurityHandler.CidrRange range = TcpSecurityHandler.CidrRange.parse("172.16.0.1");
        assertTrue(range.contains(InetAddress.getByName("172.16.0.1")));
        assertFalse(range.contains(InetAddress.getByName("172.16.0.2")));
    }

    @Test
    void rateLimiterBasic() {
        TcpRateLimiter limiter = new TcpRateLimiter(100, 10);
        assertEquals(0, limiter.currentTotal());
    }
}
