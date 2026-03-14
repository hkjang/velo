package io.velo.was.tcp.session;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TcpSessionManagerTest {

    @Test
    void createSession() {
        TcpSessionManager manager = new TcpSessionManager();
        TcpSession session = manager.createSession(
                new InetSocketAddress("127.0.0.1", 12345),
                new InetSocketAddress("0.0.0.0", 9090));

        assertNotNull(session.sessionId());
        assertEquals(1, manager.activeCount());
    }

    @Test
    void removeSession() {
        TcpSessionManager manager = new TcpSessionManager();
        TcpSession session = manager.createSession(
                new InetSocketAddress("127.0.0.1", 12345),
                new InetSocketAddress("0.0.0.0", 9090));

        TcpSession removed = manager.removeSession(session.sessionId());
        assertNotNull(removed);
        assertEquals(0, manager.activeCount());
    }

    @Test
    void getSession() {
        TcpSessionManager manager = new TcpSessionManager();
        TcpSession session = manager.createSession(
                new InetSocketAddress("127.0.0.1", 12345),
                new InetSocketAddress("0.0.0.0", 9090));

        TcpSession found = manager.getSession(session.sessionId());
        assertSame(session, found);
    }

    @Test
    void getSessionNotFound() {
        TcpSessionManager manager = new TcpSessionManager();
        assertNull(manager.getSession("nonexistent"));
    }

    @Test
    void multipleSessions() {
        TcpSessionManager manager = new TcpSessionManager();
        manager.createSession(
                new InetSocketAddress("127.0.0.1", 11111),
                new InetSocketAddress("0.0.0.0", 9090));
        manager.createSession(
                new InetSocketAddress("127.0.0.2", 22222),
                new InetSocketAddress("0.0.0.0", 9090));
        manager.createSession(
                new InetSocketAddress("127.0.0.3", 33333),
                new InetSocketAddress("0.0.0.0", 9090));

        assertEquals(3, manager.activeCount());
        assertEquals(3, manager.allSessions().size());
    }

    @Test
    void clearSessions() {
        TcpSessionManager manager = new TcpSessionManager();
        manager.createSession(
                new InetSocketAddress("127.0.0.1", 11111),
                new InetSocketAddress("0.0.0.0", 9090));
        manager.createSession(
                new InetSocketAddress("127.0.0.2", 22222),
                new InetSocketAddress("0.0.0.0", 9090));

        manager.clear();
        assertEquals(0, manager.activeCount());
    }

    @Test
    void sessionAttributes() {
        TcpSession session = new TcpSession(
                new InetSocketAddress("127.0.0.1", 12345),
                new InetSocketAddress("0.0.0.0", 9090));

        session.setAttribute("key", "value");
        assertEquals("value", (String) session.getAttribute("key"));

        session.removeAttribute("key");
        assertNull(session.getAttribute("key"));
    }

    @Test
    void sessionAuthentication() {
        TcpSession session = new TcpSession(
                new InetSocketAddress("127.0.0.1", 12345),
                new InetSocketAddress("0.0.0.0", 9090));

        assertFalse(session.isAuthenticated());
        session.authenticate("admin");
        assertTrue(session.isAuthenticated());
        assertEquals("admin", session.authenticatedUser());

        session.clearAuthentication();
        assertFalse(session.isAuthenticated());
    }
}
