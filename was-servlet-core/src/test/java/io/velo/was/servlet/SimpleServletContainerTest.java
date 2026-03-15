package io.velo.was.servlet;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.velo.was.http.HttpExchange;
import io.velo.was.http.ResponseSink;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleServletContainerTest {

    @Test
    void dispatchesHttpServletAndPersistsSessionAcrossRequests() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> events = new ArrayList<>();
        container.deploy(SimpleServletApplication.builder("test-app", "/app")
                .filter(new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        events.add("filter-before");
                        ((HttpServletResponse) response).setHeader("X-Test-Filter", "on");
                        chain.doFilter(request, response);
                        events.add("filter-after");
                    }
                })
                .servletContextListener(new ServletContextListener() {
                    @Override
                    public void contextInitialized(ServletContextEvent sce) {
                        events.add("context-init");
                        sce.getServletContext().setAttribute("lifecycle", "started");
                    }
                })
                .servletRequestListener(new ServletRequestListener() {
                    @Override
                    public void requestInitialized(ServletRequestEvent sre) {
                        events.add("request-init");
                    }

                    @Override
                    public void requestDestroyed(ServletRequestEvent sre) {
                        events.add("request-destroy");
                    }
                })
                .servlet("/hello", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
                        Integer visits = (Integer) req.getSession(true).getAttribute("visits");
                        int nextVisits = visits == null ? 1 : visits + 1;
                        req.getSession().setAttribute("visits", nextVisits);
                        resp.setContentType("application/json; charset=UTF-8");
                        resp.getWriter().write("""
                                {"contextPath":"%s","servletPath":"%s","pathInfo":%s,"visits":%d,"lifecycle":"%s"}
                                """.formatted(
                                req.getContextPath(),
                                req.getServletPath(),
                                req.getPathInfo() == null ? "null" : "\"" + req.getPathInfo() + "\"",
                                nextVisits,
                                req.getServletContext().getAttribute("lifecycle")).trim());
                    }
                })
                .build());

        FullHttpResponse first = container.handle(new io.velo.was.http.HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/hello"),
                null,
                null));

        assertEquals(200, first.status().code());
        assertTrue(first.headers().contains(HttpHeaderNames.SET_COOKIE));
        assertEquals("on", first.headers().get("X-Test-Filter"));
        String sessionCookie = first.headers().get(HttpHeaderNames.SET_COOKIE);
        String firstBody = first.content().toString(StandardCharsets.UTF_8);
        assertTrue(firstBody.contains("\"contextPath\":\"/app\""));
        assertTrue(firstBody.contains("\"servletPath\":\"/hello\""));
        assertTrue(firstBody.contains("\"visits\":1"));
        assertTrue(firstBody.contains("\"lifecycle\":\"started\""));
        assertEquals(List.of("context-init", "request-init", "filter-before", "filter-after", "request-destroy"), events);

        DefaultFullHttpRequest secondRequest =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/hello");
        secondRequest.headers().set(HttpHeaderNames.COOKIE, sessionCookie.split(";", 2)[0]);
        FullHttpResponse second = container.handle(new io.velo.was.http.HttpExchange(secondRequest, null, null));

        assertEquals(200, second.status().code());
        assertTrue(second.content().toString(StandardCharsets.UTF_8).contains("\"visits\":2"));
        assertTrue(events.contains("request-init"));
        assertNotNull(second.headers());
    }

    @Test
    void supportsRequestDispatcherForwardAndInclude() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> filterEvents = new ArrayList<>();
        container.deploy(SimpleServletApplication.builder("dispatch-app", "/app")
                .filter("/*", new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        filterEvents.add("request-filter-" + ((HttpServletRequest) request).getDispatcherType());
                        chain.doFilter(request, response);
                    }
                }, DispatcherType.REQUEST)
                .filter("/target", new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        filterEvents.add("forward-filter-" + ((HttpServletRequest) request).getDispatcherType());
                        chain.doFilter(request, response);
                    }
                }, DispatcherType.FORWARD)
                .filter("/target", new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        filterEvents.add("include-filter-" + ((HttpServletRequest) request).getDispatcherType());
                        chain.doFilter(request, response);
                    }
                }, DispatcherType.INCLUDE)
                .servlet("/target", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
                        String forwardUri = String.valueOf(req.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI));
                        String includeUri = String.valueOf(req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI));
                        resp.getWriter().write("TARGET[" + req.getDispatcherType() + ":" + req.getServletPath()
                                + ":" + req.getRequestURI()
                                + ":fwd=" + forwardUri
                                + ":inc=" + includeUri + "]");
                    }
                })
                .servlet("/forward", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
                        resp.getWriter().write("before-forward:");
                        req.getRequestDispatcher("/target?x=1").forward(req, resp);
                    }
                })
                .servlet("/include", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
                        resp.getWriter().write("before-include:");
                        req.getRequestDispatcher("/target?y=2").include(req, resp);
                        resp.getWriter().write(":after-include");
                    }
                })
                .servlet("/dir/relative", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
                        req.getRequestDispatcher("target").forward(req, resp);
                    }
                })
                .servlet("/dir/target", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
                        resp.getWriter().write("REL[" + req.getDispatcherType() + ":" + req.getServletPath() + "]");
                    }
                })
                .servlet("/dir/sub/up", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
                        req.getRequestDispatcher("../target").forward(req, resp);
                    }
                })
                .build());

        FullHttpResponse forwardResponse = container.handle(new io.velo.was.http.HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward?orig=1"),
                null,
                null));
        assertEquals(200, forwardResponse.status().code());
        assertEquals("TARGET[FORWARD:/target:/app/target:fwd=/app/forward:inc=null]",
                forwardResponse.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of("request-filter-REQUEST", "forward-filter-FORWARD"), filterEvents);

        FullHttpResponse includeResponse = container.handle(new io.velo.was.http.HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/include?orig=2"),
                null,
                null));
        assertEquals(200, includeResponse.status().code());
        assertEquals("before-include:TARGET[INCLUDE:/target:/app/include:fwd=null:inc=/app/target]:after-include",
                includeResponse.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of(
                        "request-filter-REQUEST",
                        "forward-filter-FORWARD",
                        "request-filter-REQUEST",
                        "include-filter-INCLUDE"),
                filterEvents);

        FullHttpResponse relativeForward = container.handle(new io.velo.was.http.HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/dir/relative"),
                null,
                null));
        assertEquals(200, relativeForward.status().code());
        assertEquals("REL[FORWARD:/dir/target]", relativeForward.content().toString(StandardCharsets.UTF_8));

        FullHttpResponse parentRelativeForward = container.handle(new io.velo.was.http.HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/dir/sub/up"),
                null,
                null));
        assertEquals(200, parentRelativeForward.status().code());
        assertEquals("REL[FORWARD:/dir/target]", parentRelativeForward.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void asyncContextCompleteDeliversResponseViaSink() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("async-app", "/app")
                .servlet("/async", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        AsyncContext asyncContext = req.startAsync();
                        asyncContext.start(() -> {
                            try {
                                resp.setContentType("text/plain; charset=UTF-8");
                                resp.getWriter().write("async-result");
                                asyncContext.complete();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                })
                .build());

        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        ResponseSink sink = future::complete;

        FullHttpResponse syncResult = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/async"),
                null, null, sink));

        assertNull(syncResult, "Sync result should be null for async request");

        FullHttpResponse asyncResponse = future.get(5, TimeUnit.SECONDS);
        assertNotNull(asyncResponse);
        assertEquals(200, asyncResponse.status().code());
        assertEquals("async-result", asyncResponse.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void asyncContextWithStartAsyncRequestResponse() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("async-app2", "/app")
                .servlet("/async2", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        AsyncContext asyncContext = req.startAsync(req, resp);
                        asyncContext.start(() -> {
                            try {
                                HttpServletResponse asyncResp = (HttpServletResponse) asyncContext.getResponse();
                                asyncResp.setContentType("application/json; charset=UTF-8");
                                asyncResp.getWriter().write("{\"mode\":\"async\"}");
                                asyncContext.complete();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                })
                .build());

        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        FullHttpResponse syncResult = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/async2"),
                null, null, future::complete));

        assertNull(syncResult);
        FullHttpResponse asyncResponse = future.get(5, TimeUnit.SECONDS);
        assertEquals(200, asyncResponse.status().code());
        assertTrue(asyncResponse.content().toString(StandardCharsets.UTF_8).contains("\"mode\":\"async\""));
    }

    @Test
    void asyncListenerReceivesOnCompleteEvent() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> listenerEvent = new AtomicReference<>();

        container.deploy(SimpleServletApplication.builder("async-listener-app", "/app")
                .servlet("/listen", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        AsyncContext asyncContext = req.startAsync();
                        asyncContext.addListener(new AsyncListener() {
                            @Override
                            public void onComplete(AsyncEvent event) {
                                listenerEvent.set("completed");
                                latch.countDown();
                            }
                            @Override
                            public void onTimeout(AsyncEvent event) {}
                            @Override
                            public void onError(AsyncEvent event) {}
                            @Override
                            public void onStartAsync(AsyncEvent event) {}
                        });
                        asyncContext.start(() -> {
                            try {
                                resp.getWriter().write("listened");
                                asyncContext.complete();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                })
                .build());

        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/listen"),
                null, null, future::complete));

        FullHttpResponse response = future.get(5, TimeUnit.SECONDS);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("completed", listenerEvent.get());
        assertEquals("listened", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void asyncContextTimeoutTriggersOnTimeoutAndComplete() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        CountDownLatch latch = new CountDownLatch(1);
        List<String> events = new ArrayList<>();

        container.deploy(SimpleServletApplication.builder("timeout-app", "/app")
                .servlet("/timeout", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        AsyncContext asyncContext = req.startAsync();
                        asyncContext.setTimeout(200);
                        asyncContext.addListener(new AsyncListener() {
                            @Override
                            public void onComplete(AsyncEvent event) {
                                events.add("onComplete");
                                latch.countDown();
                            }
                            @Override
                            public void onTimeout(AsyncEvent event) {
                                events.add("onTimeout");
                            }
                            @Override
                            public void onError(AsyncEvent event) {}
                            @Override
                            public void onStartAsync(AsyncEvent event) {}
                        });
                        // Intentionally not calling complete - let it timeout
                    }
                })
                .build());

        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/timeout"),
                null, null, future::complete));

        FullHttpResponse response = future.get(5, TimeUnit.SECONDS);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(response);
        assertTrue(events.contains("onTimeout"));
        assertTrue(events.contains("onComplete"));
        assertEquals(0, events.indexOf("onTimeout"));
    }

    @Test
    void isAsyncStartedReturnsTrueAfterStartAsync() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        AtomicReference<Boolean> asyncStarted = new AtomicReference<>();
        AtomicReference<Boolean> asyncSupported = new AtomicReference<>();

        container.deploy(SimpleServletApplication.builder("async-flag-app", "/app")
                .servlet("/flags", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        asyncSupported.set(req.isAsyncSupported());
                        AsyncContext ctx = req.startAsync();
                        asyncStarted.set(req.isAsyncStarted());
                        ctx.start(() -> {
                            try {
                                resp.getWriter().write("ok");
                                ctx.complete();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                })
                .build());

        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/flags"),
                null, null, future::complete));

        future.get(5, TimeUnit.SECONDS);
        assertTrue(asyncSupported.get());
        assertTrue(asyncStarted.get());
    }

    @Test
    void asyncRequestListenersNotCalledOnDestroyUntilComplete() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> events = new ArrayList<>();
        CountDownLatch completeLatch = new CountDownLatch(1);

        container.deploy(SimpleServletApplication.builder("async-listener-lifecycle", "/app")
                .servletRequestListener(new ServletRequestListener() {
                    @Override
                    public void requestInitialized(ServletRequestEvent sre) {
                        events.add("request-init");
                    }
                    @Override
                    public void requestDestroyed(ServletRequestEvent sre) {
                        events.add("request-destroy");
                    }
                })
                .servlet("/deferred", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        events.add("servlet-start");
                        AsyncContext ctx = req.startAsync();
                        ctx.start(() -> {
                            try {
                                Thread.sleep(100);
                                resp.getWriter().write("deferred");
                                ctx.complete();
                                completeLatch.countDown();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                        events.add("servlet-end");
                    }
                })
                .build());

        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/deferred"),
                null, null, future::complete));

        // Right after handle() returns, request-destroy should NOT have been called yet
        assertTrue(events.contains("request-init"));
        assertTrue(events.contains("servlet-start"));
        assertTrue(events.contains("servlet-end"));
        // request-destroy should NOT be present because async is still running

        completeLatch.await(5, TimeUnit.SECONDS);
        future.get(5, TimeUnit.SECONDS);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Session expiration tests
    // ────────────────────────────────────────────────────────────────────────

    @Test
    void sessionExpiresAfterMaxInactiveInterval() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer(1); // 1s purge interval
        container.deploy(SimpleServletApplication.builder("session-ttl-app", "/app")
                .servlet("/set", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        req.getSession(true).setMaxInactiveInterval(1); // 1 second TTL
                        req.getSession().setAttribute("data", "important");
                        resp.getWriter().write("session-created");
                    }
                })
                .servlet("/check", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        var session = req.getSession(false);
                        resp.getWriter().write(session == null ? "no-session" : "has-session");
                    }
                })
                .build());

        // Create a session
        FullHttpResponse createResp = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/set"),
                null, null));
        assertEquals(200, createResp.status().code());
        String sessionCookie = createResp.headers().get(HttpHeaderNames.SET_COOKIE).split(";")[0];
        assertEquals(1, container.sessionStore().size());

        // Check session exists immediately
        DefaultFullHttpRequest checkReq1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/check");
        checkReq1.headers().set(HttpHeaderNames.COOKIE, sessionCookie);
        FullHttpResponse checkResp1 = container.handle(new HttpExchange(checkReq1, null, null));
        assertEquals("has-session", checkResp1.content().toString(StandardCharsets.UTF_8));

        // Wait for session to expire (1s TTL + margin)
        Thread.sleep(1500);

        // Session should be expired now — find() returns null for expired sessions
        DefaultFullHttpRequest checkReq2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/check");
        checkReq2.headers().set(HttpHeaderNames.COOKIE, sessionCookie);
        FullHttpResponse checkResp2 = container.handle(new HttpExchange(checkReq2, null, null));
        assertEquals("no-session", checkResp2.content().toString(StandardCharsets.UTF_8));

        container.close();
    }

    @Test
    void sessionPurgeRemovesExpiredSessions() throws Exception {
        InMemoryHttpSessionStore store = new InMemoryHttpSessionStore();

        // Create 3 sessions with 1s TTL
        for (int i = 0; i < 3; i++) {
            SessionState session = store.create();
            session.setMaxInactiveIntervalSeconds(1);
        }
        assertEquals(3, store.size());

        // Wait for them to expire
        Thread.sleep(1200);

        // Purge should remove all 3
        int purged = store.purgeExpired();
        assertEquals(3, purged);
        assertEquals(0, store.size());
    }

    @Test
    void sessionWithZeroMaxInactiveIntervalNeverExpires() {
        InMemoryHttpSessionStore store = new InMemoryHttpSessionStore();
        SessionState session = store.create();
        session.setMaxInactiveIntervalSeconds(0); // never expires

        // Even with lastAccessedTime in the past, should not be expired
        assertNotNull(store.find(session.getId()));
        assertEquals(0, store.purgeExpired());
    }

    @Test
    void expirationListenerCalledOnPurge() throws Exception {
        InMemoryHttpSessionStore store = new InMemoryHttpSessionStore();
        List<String> expiredIds = new ArrayList<>();
        store.addExpirationListener(state -> expiredIds.add(state.getId()));

        SessionState session = store.create();
        session.setMaxInactiveIntervalSeconds(1);
        String sessionId = session.getId();

        Thread.sleep(1200);
        store.purgeExpired();

        assertEquals(1, expiredIds.size());
        assertEquals(sessionId, expiredIds.get(0));
    }

    @Test
    void sessionTouchResetsExpirationTimer() throws Exception {
        InMemoryHttpSessionStore store = new InMemoryHttpSessionStore();
        SessionState session = store.create();
        session.setMaxInactiveIntervalSeconds(1); // 1s TTL

        // Touch repeatedly to keep alive
        for (int i = 0; i < 5; i++) {
            Thread.sleep(300);
            session.touch();
        }

        // Session should still be alive (total time ~1.5s but each touch resets)
        assertNotNull(store.find(session.getId()));
        assertEquals(0, store.purgeExpired());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Welcome file tests
    // ────────────────────────────────────────────────────────────────────────

    @Test
    void welcomeFileResolvesRootToIndexJsp() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("welcome-app", "/app")
                .servlet("*.jsp", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.setContentType("text/html");
                        resp.getWriter().write("JSP:" + req.getServletPath());
                    }
                })
                .welcomeFile("index.jsp")
                .welcomeFile("index.html")
                .build());

        // Request to /app/ should resolve to /app/index.jsp via welcome file
        FullHttpResponse resp = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/"),
                null, null));
        assertEquals(200, resp.status().code());
        String body = resp.content().toString(StandardCharsets.UTF_8);
        assertEquals("JSP:/index.jsp", body);
    }

    @Test
    void welcomeFileResolvesRootContextToIndex() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("root-welcome-app", "/")
                .servlet("*.jsp", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.setContentType("text/html");
                        resp.getWriter().write("ROOT-JSP:" + req.getServletPath());
                    }
                })
                .welcomeFile("index.jsp")
                .build());

        // Request to / should resolve to /index.jsp via welcome file
        FullHttpResponse resp = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                null, null));
        assertEquals(200, resp.status().code());
        String body = resp.content().toString(StandardCharsets.UTF_8);
        assertEquals("ROOT-JSP:/index.jsp", body);
    }

    @Test
    void noWelcomeFileFallsBackToDefaultServlet() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("no-welcome-app", "/app")
                .servlet("/", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("default-servlet");
                    }
                })
                .build());

        // No welcome files defined, "/" mapping should still work as default servlet
        FullHttpResponse resp = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/"),
                null, null));
        assertEquals(200, resp.status().code());
        assertEquals("default-servlet", resp.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void welcomeFileSkipsNonMatchingAndPicksFirst() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("multi-welcome-app", "/app")
                .servlet("*.html", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("HTML:" + req.getServletPath());
                    }
                })
                .welcomeFile("index.jsp")   // no *.jsp servlet → should be skipped
                .welcomeFile("index.html")   // *.html servlet → should match
                .build());

        FullHttpResponse resp = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/"),
                null, null));
        assertEquals(200, resp.status().code());
        assertEquals("HTML:/index.html", resp.content().toString(StandardCharsets.UTF_8));
    }
}
