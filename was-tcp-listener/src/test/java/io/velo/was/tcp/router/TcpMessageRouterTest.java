package io.velo.was.tcp.router;

import io.velo.was.tcp.codec.TcpMessage;
import io.velo.was.tcp.session.TcpSession;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TcpMessageRouterTest {

    @Test
    void routeToRegisteredHandler() {
        TcpMessageRouter router = new TcpMessageRouter();
        AtomicReference<String> handled = new AtomicReference<>();

        router.route("ECHO", (session, message, sender) ->
                handled.set("ECHO:" + new String(message.payload())));

        TcpSession session = createSession();
        TcpMessage message = new TcpMessage("ECHO", "hello".getBytes());

        assertTrue(router.dispatch(session, message, null));
        assertEquals("ECHO:hello", handled.get());
    }

    @Test
    void routeToFallback() {
        TcpMessageRouter router = new TcpMessageRouter();
        AtomicReference<String> handled = new AtomicReference<>();

        router.fallback((session, message, sender) ->
                handled.set("FALLBACK:" + message.messageType()));

        TcpSession session = createSession();
        TcpMessage message = new TcpMessage("UNKNOWN", "data".getBytes());

        assertTrue(router.dispatch(session, message, null));
        assertEquals("FALLBACK:UNKNOWN", handled.get());
    }

    @Test
    void noHandlerRegistered() {
        TcpMessageRouter router = new TcpMessageRouter();
        TcpSession session = createSession();
        TcpMessage message = new TcpMessage("UNKNOWN", "data".getBytes());

        assertFalse(router.dispatch(session, message, null));
    }

    @Test
    void multipleRoutes() {
        TcpMessageRouter router = new TcpMessageRouter();
        AtomicReference<String> handled = new AtomicReference<>();

        router.route("A", (s, m, r) -> handled.set("A"))
              .route("B", (s, m, r) -> handled.set("B"))
              .route("C", (s, m, r) -> handled.set("C"));

        assertEquals(3, router.routeCount());

        router.dispatch(createSession(), new TcpMessage("B", new byte[0]), null);
        assertEquals("B", handled.get());
    }

    @Test
    void removeRoute() {
        TcpMessageRouter router = new TcpMessageRouter();
        router.route("TEST", (s, m, r) -> {});
        assertTrue(router.hasRoute("TEST"));

        router.removeRoute("TEST");
        assertFalse(router.hasRoute("TEST"));
    }

    @Test
    void hasRouteWithFallback() {
        TcpMessageRouter router = new TcpMessageRouter();
        router.fallback((s, m, r) -> {});

        assertTrue(router.hasRoute("ANYTHING"));
    }

    private TcpSession createSession() {
        return new TcpSession(
                new InetSocketAddress("127.0.0.1", 12345),
                new InetSocketAddress("0.0.0.0", 9090));
    }
}
