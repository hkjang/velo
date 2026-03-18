package io.velo.was.servlet;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.velo.was.http.HttpExchange;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusteredHttpSessionStoreTest {

    @Test
    void sessionSurvivesNodeHopAndKeepsStickyRoute() throws Exception {
        InMemorySessionRepository repository = new InMemorySessionRepository();
        InMemorySessionReplicationChannel replicationChannel = new InMemorySessionReplicationChannel();
        ClusteredHttpSessionStore storeA = new ClusteredHttpSessionStore(
                "node-a",
                "node-a",
                true,
                1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);
        ClusteredHttpSessionStore storeB = new ClusteredHttpSessionStore(
                "node-b",
                "node-b",
                true,
                1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);
        SimpleServletContainer nodeA = new SimpleServletContainer(storeA, 1);
        SimpleServletContainer nodeB = new SimpleServletContainer(storeB, 1);

        try {
            nodeA.deploy(counterApplication("app-a"));
            nodeB.deploy(counterApplication("app-b"));

            FullHttpResponse first = nodeA.handle(new HttpExchange(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/counter"),
                    null,
                    null));
            String sessionCookie = first.headers().get(HttpHeaderNames.SET_COOKIE);
            String cookieHeader = sessionCookie.split(";", 2)[0];
            assertTrue(cookieHeader.startsWith("JSESSIONID=node-a."));
            assertTrue(first.content().toString(StandardCharsets.UTF_8).contains("app-a:1"));

            DefaultFullHttpRequest secondRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/counter");
            secondRequest.headers().set(HttpHeaderNames.COOKIE, cookieHeader);
            FullHttpResponse second = nodeB.handle(new HttpExchange(secondRequest, null, null));
            assertTrue(second.content().toString(StandardCharsets.UTF_8).contains("app-b:2"));

            String sessionId = cookieHeader.substring("JSESSIONID=".length());
            awaitTrue(() -> {
                SessionState state = storeA.find(sessionId);
                Object visits = state == null ? null : state.attributes().get("visits");
                return Integer.valueOf(2).equals(visits);
            }, 2_000);

            DefaultFullHttpRequest thirdRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/counter");
            thirdRequest.headers().set(HttpHeaderNames.COOKIE, cookieHeader);
            FullHttpResponse third = nodeA.handle(new HttpExchange(thirdRequest, null, null));
            assertTrue(third.content().toString(StandardCharsets.UTF_8).contains("app-a:3"));
        } finally {
            nodeA.close();
            nodeB.close();
            replicationChannel.close();
        }
    }

    @Test
    void latestWriteWinsWhenTwoNodesUpdateSameSession() throws Exception {
        InMemorySessionRepository repository = new InMemorySessionRepository();
        InMemorySessionReplicationChannel replicationChannel = new InMemorySessionReplicationChannel();
        ClusteredHttpSessionStore storeA = new ClusteredHttpSessionStore(
                "node-a",
                "node-a",
                true,
                1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);
        ClusteredHttpSessionStore storeB = new ClusteredHttpSessionStore(
                "node-b",
                "node-b",
                true,
                1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);

        try {
            SessionState sessionOnA = storeA.create();
            sessionOnA.attributes().put("owner", "base");

            SessionState sessionOnB = storeB.find(sessionOnA.getId());
            assertNotNull(sessionOnB);

            sessionOnA.attributes().put("owner", "node-a");
            Thread.sleep(25);
            sessionOnB.attributes().put("owner", "node-b");

            awaitTrue(() -> {
                SessionState a = storeA.find(sessionOnA.getId());
                SessionState b = storeB.find(sessionOnA.getId());
                return "node-b".equals(a.attributes().get("owner"))
                        && "node-b".equals(b.attributes().get("owner"));
            }, 2_000);
        } finally {
            storeA.close();
            storeB.close();
            replicationChannel.close();
        }
    }

    @Test
    void ttlPurgeIsConsistentAcrossNodes() throws Exception {
        InMemorySessionRepository repository = new InMemorySessionRepository();
        InMemorySessionReplicationChannel replicationChannel = new InMemorySessionReplicationChannel();
        ClusteredHttpSessionStore storeA = new ClusteredHttpSessionStore(
                "node-a",
                "node-a",
                true,
                1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);
        ClusteredHttpSessionStore storeB = new ClusteredHttpSessionStore(
                "node-b",
                "node-b",
                true,
                1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);

        try {
            SessionState session = storeA.create();
            session.setMaxInactiveIntervalSeconds(1);
            session.attributes().put("scope", "cluster");

            assertNotNull(storeB.find(session.getId()));

            Thread.sleep(1_250);
            assertTrue(storeB.purgeExpired() >= 1);

            awaitTrue(() -> storeA.find(session.getId()) == null, 2_000);
            assertNull(storeB.find(session.getId()));
        } finally {
            storeA.close();
            storeB.close();
            replicationChannel.close();
        }
    }

    @Test
    void strictSerializationPolicyRejectsNonSerializableAttributes() {
        ClusteredHttpSessionStore store = new ClusteredHttpSessionStore(
                "node-a",
                "node-a",
                true,
                1800,
                new InMemorySessionRepository(),
                SessionReplicationChannel.NO_OP,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);
        try {
            SessionState session = store.create();
            assertThrows(IllegalArgumentException.class, () -> session.attributes().put("bad", new Object()));
        } finally {
            store.close();
        }
    }

    private static SimpleServletApplication counterApplication(String label) {
        return SimpleServletApplication.builder(label, "/app")
                .servlet("/counter", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        Integer visits = (Integer) req.getSession(true).getAttribute("visits");
                        int nextVisits = visits == null ? 1 : visits + 1;
                        req.getSession().setAttribute("visits", nextVisits);
                        resp.getWriter().write(label + ":" + nextVisits);
                    }
                })
                .build();
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not satisfied within timeout");
    }
}
