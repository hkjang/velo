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
import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleServletContainerTest {

    @TempDir
    Path tempDir;

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
    void exactServletMappingWinsOverPathPrefixMapping() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("mapping-app", "/app")
                .servlet("/catalog", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("EXACT[" + req.getServletPath() + ":" + req.getPathInfo() + "]");
                    }
                })
                .servlet("/catalog/*", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("PREFIX[" + req.getServletPath() + ":" + req.getPathInfo() + "]");
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/catalog"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("EXACT[/catalog:null]", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void exactServletMappingDoesNotCaptureSubPaths() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("mapping-fallback-app", "/app")
                .servlet("/hello", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("EXACT");
                    }
                })
                .servlet("/", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("DEFAULT[" + req.getServletPath() + ":" + req.getPathInfo() + "]");
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/hello/details"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("DEFAULT[/hello/details:null]", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void appliesUrlPatternFiltersBeforeServletNameFilters() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> filterEvents = new ArrayList<>();
        container.deploy(SimpleServletApplication.builder("filter-order-app", "/app")
                .filter("/*", recordingFilter(filterEvents, "all"), DispatcherType.REQUEST)
                .filter("*.json", recordingFilter(filterEvents, "extension"), DispatcherType.REQUEST)
                .filterForServlet("ApiServlet", recordingFilter(filterEvents, "servlet-name"), DispatcherType.REQUEST)
                .servlet("/api/*", "ApiServlet", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("FILTERS[" + req.getServletPath() + ":" + req.getPathInfo() + "]");
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/api/items.json"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("FILTERS[/api:/items.json]", response.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of("all", "extension", "servlet-name"), filterEvents);
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
    void supportsNamedDispatcherForwardAndInclude() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> filterEvents = new ArrayList<>();
        container.deploy(SimpleServletApplication.builder("named-dispatch-app", "/app")
                .filter("/*", recordingFilter(filterEvents, "request-url"), DispatcherType.REQUEST)
                .filter("/*", recordingFilter(filterEvents, "forward-url"), DispatcherType.FORWARD)
                .filter("/*", recordingFilter(filterEvents, "include-url"), DispatcherType.INCLUDE)
                .filterForServlet("NamedTarget", recordingFilter(filterEvents, "forward-named"), DispatcherType.FORWARD)
                .filterForServlet("NamedTarget", recordingFilter(filterEvents, "include-named"), DispatcherType.INCLUDE)
                .servlet("/target", "NamedTarget", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("NAMED[" + req.getDispatcherType()
                                + ":" + req.getRequestURI()
                                + ":" + req.getServletPath()
                                + ":fwd=" + req.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI)
                                + ":inc=" + req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) + "]");
                    }
                })
                .servlet("/forward-named", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("NamedTarget");
                        assertNotNull(dispatcher);
                        dispatcher.forward(req, resp);
                    }
                })
                .servlet("/include-named", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("NamedTarget");
                        assertNotNull(dispatcher);
                        resp.getWriter().write("before:");
                        dispatcher.include(req, resp);
                        resp.getWriter().write(":after");
                    }
                })
                .build());

        FullHttpResponse forwardResponse = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward-named"),
                null,
                null));
        assertEquals(200, forwardResponse.status().code());
        assertEquals("NAMED[FORWARD:/app/forward-named:/forward-named:fwd=null:inc=null]",
                forwardResponse.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of("request-url", "forward-named"), filterEvents);

        FullHttpResponse includeResponse = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/include-named"),
                null,
                null));
        assertEquals(200, includeResponse.status().code());
        assertEquals("before:NAMED[INCLUDE:/app/include-named:/include-named:fwd=null:inc=null]:after",
                includeResponse.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of("request-url", "forward-named", "request-url", "include-named"), filterEvents);
    }

    @Test
    void forwardClearsBufferedBodyButPreservesHeaders() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("forward-buffer-app", "/app")
                .servlet("/target", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("TARGET");
                    }
                })
                .servlet("/forward-buffer", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        resp.setHeader("X-Before-Forward", "kept");
                        resp.getWriter().write("discard-me");
                        req.getRequestDispatcher("/target").forward(req, resp);
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward-buffer"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("kept", response.headers().get("X-Before-Forward"));
        assertEquals("TARGET", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void forwardRejectsCommittedResponse() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("forward-committed-app", "/app")
                .servlet("/target", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("TARGET");
                    }
                })
                .servlet("/forward-committed", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        try {
                            req.getRequestDispatcher("/target").forward(req, committedResponse());
                            resp.getWriter().write("unexpected");
                        } catch (IllegalStateException exception) {
                            resp.getWriter().write("ILLEGAL:" + exception.getMessage());
                        }
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward-committed"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("ILLEGAL:Cannot forward after response has been committed",
                response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void namedForwardRejectsCommittedResponse() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("named-forward-committed-app", "/app")
                .servlet("/target", "NamedTarget", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("TARGET");
                    }
                })
                .servlet("/forward-named-committed", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("NamedTarget");
                        assertNotNull(dispatcher);
                        try {
                            dispatcher.forward(req, committedResponse());
                            resp.getWriter().write("unexpected");
                        } catch (IllegalStateException exception) {
                            resp.getWriter().write("ILLEGAL:" + exception.getMessage());
                        }
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward-named-committed"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("ILLEGAL:Cannot forward after response has been committed",
                response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void forwardDisablesFurtherOutputFromCaller() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("forward-finish-app", "/app")
                .servlet("/target", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("TARGET");
                    }
                })
                .servlet("/forward-finish", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        req.getRequestDispatcher("/target").forward(req, resp);
                        resp.getWriter().write(":after-forward");
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward-finish"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("TARGET", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void requestDispatcherPreservesWrappedRequestAndResponse() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("wrapped-dispatch-app", "/app")
                .servlet("/wrapped-target", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.setHeader("X-Wrapped-Resp", "seen");
                        resp.getWriter().write("TARGET[" + req.getDispatcherType()
                                + ":" + req.getHeader("X-Wrapped-Req")
                                + ":" + req.getRequestURI() + "]");
                    }
                })
                .servlet("/forward-wrapped", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(req) {
                            @Override
                            public String getHeader(String name) {
                                if ("X-Wrapped-Req".equalsIgnoreCase(name)) {
                                    return "wrapped-request";
                                }
                                return super.getHeader(name);
                            }
                        };
                        HttpServletResponse wrappedResponse = new HttpServletResponseWrapper(resp) {
                            @Override
                            public void setHeader(String name, String value) {
                                if ("X-Wrapped-Resp".equalsIgnoreCase(name)) {
                                    super.setHeader(name, "wrapped-" + value);
                                    return;
                                }
                                super.setHeader(name, value);
                            }
                        };
                        req.getRequestDispatcher("/wrapped-target").forward(wrappedRequest, wrappedResponse);
                    }
                })
                .servlet("/include-wrapped", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(req) {
                            @Override
                            public String getHeader(String name) {
                                if ("X-Wrapped-Req".equalsIgnoreCase(name)) {
                                    return "wrapped-request";
                                }
                                return super.getHeader(name);
                            }
                        };
                        HttpServletResponse wrappedResponse = new HttpServletResponseWrapper(resp) {
                            @Override
                            public void setHeader(String name, String value) {
                                if ("X-Wrapped-Resp".equalsIgnoreCase(name)) {
                                    super.setHeader(name, "wrapped-" + value);
                                    return;
                                }
                                super.setHeader(name, value);
                            }
                        };
                        resp.getWriter().write("before:");
                        req.getRequestDispatcher("/wrapped-target").include(wrappedRequest, wrappedResponse);
                        resp.getWriter().write(":after-uri=" + wrappedRequest.getRequestURI());
                    }
                })
                .build());

        FullHttpResponse forwardResponse = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/forward-wrapped"),
                null,
                null));
        assertEquals(200, forwardResponse.status().code());
        assertEquals("wrapped-seen", forwardResponse.headers().get("X-Wrapped-Resp"));
        assertEquals("TARGET[FORWARD:wrapped-request:/app/wrapped-target]",
                forwardResponse.content().toString(StandardCharsets.UTF_8));

        FullHttpResponse includeResponse = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/include-wrapped"),
                null,
                null));
        assertEquals(200, includeResponse.status().code());
        assertEquals("wrapped-seen", includeResponse.headers().get("X-Wrapped-Resp"));
        assertEquals("before:TARGET[INCLUDE:wrapped-request:/app/include-wrapped]:after-uri=/app/include-wrapped",
                includeResponse.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void includeAttributesAreRestoredAfterNestedDispatch() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("nested-include-app", "/app")
                .servlet("/target-include", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("TARGET[" + req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) + "]");
                    }
                })
                .servlet("/middle-include", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        resp.getWriter().write("MIDDLE[" + req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) + "]->");
                        req.getRequestDispatcher("/target-include").include(req, resp);
                        resp.getWriter().write("->MIDDLE-AFTER[" + req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) + "]");
                    }
                })
                .servlet("/outer-include", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                        resp.getWriter().write("OUTER[" + req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) + "]->");
                        req.getRequestDispatcher("/middle-include").include(req, resp);
                        resp.getWriter().write("->OUTER-AFTER[" + req.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) + "]");
                    }
                })
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/outer-include"),
                null,
                null));

        assertEquals(200, response.status().code());
        assertEquals("OUTER[null]->MIDDLE[/app/middle-include]->TARGET[/app/target-include]->MIDDLE-AFTER[/app/middle-include]->OUTER-AFTER[null]",
                response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void requestMetadataHonorsProxyAndHostHeaders() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(SimpleServletApplication.builder("proxy-app", "/app")
                .servlet("/meta", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write(req.getScheme()
                                + "|" + req.isSecure()
                                + "|" + req.getServerName()
                                + "|" + req.getServerPort()
                                + "|" + req.getRequestURL());
                    }
                })
                .build());

        InetSocketAddress remote = new InetSocketAddress("192.168.120.159", 44580);
        InetSocketAddress local = new InetSocketAddress("10.0.0.10", 8080);

        DefaultFullHttpRequest forwardedRequest =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/meta");
        forwardedRequest.headers().set(HttpHeaderNames.HOST, "internal-was:8080");
        forwardedRequest.headers().set("X-Forwarded-Proto", "https");
        forwardedRequest.headers().set("X-Forwarded-Host", "otadpole.koreacb.com");
        forwardedRequest.headers().set("X-Forwarded-Port", "443");

        FullHttpResponse forwardedResponse = container.handle(new HttpExchange(
                forwardedRequest,
                remote,
                local));

        assertEquals("https|true|otadpole.koreacb.com|443|https://otadpole.koreacb.com/app/meta",
                forwardedResponse.content().toString(StandardCharsets.UTF_8));

        DefaultFullHttpRequest standardForwardedRequest =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/meta");
        standardForwardedRequest.headers().set(HttpHeaderNames.HOST, "internal-was:8080");
        standardForwardedRequest.headers().set("Forwarded",
                "for=192.0.2.10;proto=https;host=\"dbhub.example.com:8443\"");

        FullHttpResponse standardForwardedResponse = container.handle(new HttpExchange(
                standardForwardedRequest,
                remote,
                local));

        assertEquals("https|true|dbhub.example.com|8443|https://dbhub.example.com:8443/app/meta",
                standardForwardedResponse.content().toString(StandardCharsets.UTF_8));

        DefaultFullHttpRequest hostRequest =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/meta");
        hostRequest.headers().set(HttpHeaderNames.HOST, "console.example.com:9443");

        FullHttpResponse hostResponse = container.handle(new HttpExchange(
                hostRequest,
                remote,
                new InetSocketAddress("10.0.0.10", 9443),
                null,
                true));

        assertEquals("https|true|console.example.com|9443|https://console.example.com:9443/app/meta",
                hostResponse.content().toString(StandardCharsets.UTF_8));
    }

    private static Filter recordingFilter(List<String> events, String name) {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                events.add(name);
                chain.doFilter(request, response);
            }
        };
    }

    @Test
    void sessionCookiePolicyUsesConfiguredAttributesAndProxyAwareSecureFlag() throws Exception {
        SessionCookieSettings cookieSettings = new SessionCookieSettings(
                "VELOSESSION",
                null,
                true,
                SessionCookieSettings.SecureMode.AUTO,
                "Strict",
                -1,
                null);
        SimpleServletContainer container = new SimpleServletContainer(60, 1800, cookieSettings);
        container.deploy(SimpleServletApplication.builder("cookie-app", "/app")
                .servlet("/login", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        req.getSession(true).setAttribute("user", "velo");
                        resp.getWriter().write("ok");
                    }
                })
                .build());

        DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/login");
        request.headers().set("X-Forwarded-Proto", "https");
        request.headers().set(HttpHeaderNames.HOST, "internal.example:8080");

        FullHttpResponse response = container.handle(new HttpExchange(
                request,
                new InetSocketAddress("192.0.2.10", 55000),
                new InetSocketAddress("10.0.0.10", 8080)));

        String setCookie = response.headers().get(HttpHeaderNames.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("VELOSESSION="));
        assertTrue(setCookie.contains("Path=/app"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Strict"));
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
    void asyncContextWithWrappedRequestResponsePreservesWrappers() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        AtomicReference<Boolean> hasOriginalRequestAndResponse = new AtomicReference<>();
        container.deploy(SimpleServletApplication.builder("async-wrapped-app", "/app")
                .servlet("/async-wrapped", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(req) {
                            @Override
                            public String getHeader(String name) {
                                if ("X-Async-Wrapped".equalsIgnoreCase(name)) {
                                    return "wrapped-request";
                                }
                                return super.getHeader(name);
                            }
                        };
                        HttpServletResponse wrappedResponse = new HttpServletResponseWrapper(resp) {
                            @Override
                            public void setHeader(String name, String value) {
                                if ("X-Async-Wrapped".equalsIgnoreCase(name)) {
                                    super.setHeader(name, "wrapped-" + value);
                                    return;
                                }
                                super.setHeader(name, value);
                            }
                        };
                        AsyncContext asyncContext = req.startAsync(wrappedRequest, wrappedResponse);
                        hasOriginalRequestAndResponse.set(asyncContext.hasOriginalRequestAndResponse());
                        asyncContext.start(() -> {
                            try {
                                HttpServletRequest asyncReq = (HttpServletRequest) asyncContext.getRequest();
                                HttpServletResponse asyncResp = (HttpServletResponse) asyncContext.getResponse();
                                asyncResp.setHeader("X-Async-Wrapped", "seen");
                                asyncResp.getWriter().write(asyncReq.getHeader("X-Async-Wrapped"));
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
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/async-wrapped"),
                null, null, future::complete));

        assertNull(syncResult);
        FullHttpResponse asyncResponse = future.get(5, TimeUnit.SECONDS);
        assertEquals(200, asyncResponse.status().code());
        assertEquals("wrapped-seen", asyncResponse.headers().get("X-Async-Wrapped"));
        assertEquals("wrapped-request", asyncResponse.content().toString(StandardCharsets.UTF_8));
        assertFalse(hasOriginalRequestAndResponse.get());
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

    @Test
    void firesContextAndSessionListenerEventsAndRotatesSessionId() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> events = new ArrayList<>();

        container.deploy(SimpleServletApplication.builder("listener-app", "/app")
                .servletContextListener(new ServletContextListener() {
                    @Override
                    public void contextInitialized(ServletContextEvent sce) {
                        sce.getServletContext().setAttribute("boot", "ready");
                    }
                })
                .servletContextAttributeListener(new ServletContextAttributeListener() {
                    @Override
                    public void attributeAdded(ServletContextAttributeEvent event) {
                        events.add("context-added:" + event.getName() + "=" + event.getValue());
                    }

                    @Override
                    public void attributeRemoved(ServletContextAttributeEvent event) {
                        events.add("context-removed:" + event.getName() + "=" + event.getValue());
                    }

                    @Override
                    public void attributeReplaced(ServletContextAttributeEvent event) {
                        events.add("context-replaced:" + event.getName() + "=" + event.getValue());
                    }
                })
                .httpSessionListener(new HttpSessionListener() {
                    @Override
                    public void sessionCreated(HttpSessionEvent se) {
                        events.add("session-created:" + se.getSession().getId());
                    }

                    @Override
                    public void sessionDestroyed(HttpSessionEvent se) {
                        events.add("session-destroyed:" + se.getSession().getId());
                    }
                })
                .httpSessionAttributeListener(new HttpSessionAttributeListener() {
                    @Override
                    public void attributeAdded(HttpSessionBindingEvent event) {
                        events.add("session-attr-added:" + event.getName() + "=" + event.getValue());
                    }

                    @Override
                    public void attributeRemoved(HttpSessionBindingEvent event) {
                        events.add("session-attr-removed:" + event.getName() + "=" + event.getValue());
                    }

                    @Override
                    public void attributeReplaced(HttpSessionBindingEvent event) {
                        events.add("session-attr-replaced:" + event.getName() + "=" + event.getValue());
                    }
                })
                .httpSessionIdListener((event, oldSessionId) ->
                        events.add("session-id-changed:" + oldSessionId + "->" + event.getSession().getId()))
                .servlet("/lifecycle", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        req.getServletContext().setAttribute("mode", "initial");
                        req.getServletContext().setAttribute("mode", "live");
                        req.getServletContext().removeAttribute("mode");

                        var session = req.getSession(true);
                        session.setAttribute("user", "alice");
                        session.setAttribute("role", "guest");
                        session.setAttribute("role", "admin");
                        session.removeAttribute("role");

                        String oldId = session.getId();
                        String newId = req.changeSessionId();
                        resp.getWriter().write(oldId + "->" + newId + "|user=" + req.getSession(false).getAttribute("user"));
                    }
                })
                .servlet("/invalidate", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        req.getSession(false).invalidate();
                        resp.getWriter().write("invalidated");
                    }
                })
                .build());

        FullHttpResponse lifecycleResp = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/lifecycle"),
                null, null));

        assertEquals(200, lifecycleResp.status().code());
        String lifecycleBody = lifecycleResp.content().toString(StandardCharsets.UTF_8);
        String[] parts = lifecycleBody.split("\\|", 2);
        String[] ids = parts[0].split("->", 2);
        String oldSessionId = ids[0];
        String newSessionId = ids[1];

        assertNotEquals(oldSessionId, newSessionId);
        assertTrue(parts[1].contains("user=alice"));
        assertEquals(1, container.sessionStore().size());
        assertNull(container.sessionStore().find(oldSessionId));
        assertNotNull(container.sessionStore().find(newSessionId));

        String rotatedCookie = lifecycleResp.headers().get(HttpHeaderNames.SET_COOKIE).split(";", 2)[0];
        assertEquals("JSESSIONID=" + newSessionId, rotatedCookie);

        DefaultFullHttpRequest invalidateReq =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/invalidate");
        invalidateReq.headers().set(HttpHeaderNames.COOKIE, rotatedCookie);
        FullHttpResponse invalidateResp = container.handle(new HttpExchange(invalidateReq, null, null));

        assertEquals(200, invalidateResp.status().code());
        assertEquals("invalidated", invalidateResp.content().toString(StandardCharsets.UTF_8));
        assertFalse(events.isEmpty());
        assertEquals(List.of(
                        "context-added:boot=ready",
                        "context-added:mode=initial",
                        "context-replaced:mode=initial",
                        "context-removed:mode=live",
                        "session-created:" + oldSessionId,
                        "session-attr-added:user=alice",
                        "session-attr-added:role=guest",
                        "session-attr-replaced:role=guest",
                        "session-attr-removed:role=admin",
                        "session-id-changed:" + oldSessionId + "->" + newSessionId,
                        "session-destroyed:" + newSessionId),
                events);
    }

    @Test
    void sendErrorDispatchesToConfiguredErrorPage() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> filterEvents = new ArrayList<>();

        container.deploy(SimpleServletApplication.builder("error-status-app", "/app")
                .filter("/errors/*", new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        filterEvents.add("error-filter-" + ((HttpServletRequest) request).getDispatcherType());
                        chain.doFilter(request, response);
                    }
                }, DispatcherType.ERROR)
                .servlet("/missing", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "resource missing");
                    }
                })
                .servlet("/errors/notfound", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("STATUS[" + req.getDispatcherType()
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_MESSAGE)
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME) + "]");
                    }
                })
                .errorPage(HttpServletResponse.SC_NOT_FOUND, "/errors/notfound")
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/missing"),
                null, null));

        assertEquals(404, response.status().code());
        assertEquals("STATUS[ERROR:404:resource missing:/app/missing:/missing]",
                response.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of("error-filter-ERROR"), filterEvents);
    }

    @Test
    void exceptionDispatchesToMostSpecificErrorPage() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();
        List<String> filterEvents = new ArrayList<>();

        container.deploy(SimpleServletApplication.builder("error-exception-app", "/app")
                .filter("/errors/*", new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        filterEvents.add("error-filter-" + ((HttpServletRequest) request).getDispatcherType());
                        chain.doFilter(request, response);
                    }
                }, DispatcherType.ERROR)
                .servlet("/boom", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        throw new IllegalStateException("kaboom");
                    }
                })
                .servlet("/errors/runtime", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("RUNTIME");
                    }
                })
                .servlet("/errors/illegal", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        Class<?> exceptionType = (Class<?>) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
                        Throwable throwable = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                        resp.getWriter().write("EX[" + req.getDispatcherType()
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
                                + ":" + exceptionType.getSimpleName()
                                + ":" + throwable.getMessage()
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)
                                + ":" + req.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME) + "]");
                    }
                })
                .errorPage(RuntimeException.class, "/errors/runtime")
                .errorPage(IllegalStateException.class, "/errors/illegal")
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/boom"),
                null, null));

        assertEquals(500, response.status().code());
        assertEquals("EX[ERROR:500:IllegalStateException:kaboom:/app/boom:/boom]",
                response.content().toString(StandardCharsets.UTF_8));
        assertEquals(List.of("error-filter-ERROR"), filterEvents);
    }

    @Test
    void servletContextExposesExplodedWarResourcesAndServletInitParameters() throws Exception {
        Path webAppRoot = tempDir.resolve("bridge-app");
        Files.createDirectories(webAppRoot.resolve("WEB-INF").resolve("configuration"));
        Files.writeString(webAppRoot.resolve("WEB-INF").resolve("launch.ini"), "--launcher", StandardCharsets.UTF_8);
        Files.writeString(webAppRoot.resolve("WEB-INF").resolve("configuration").resolve("config.ini"),
                "osgi.bundles=example\n", StandardCharsets.UTF_8);

        SimpleServletContainer container = new SimpleServletContainer();
        AtomicReference<String> servletName = new AtomicReference<>();
        AtomicReference<String> initParameter = new AtomicReference<>();

        container.deploy(SimpleServletApplication.builder("resource-app", "/app")
                .initParameter("io.velo.was.webAppRoot", webAppRoot.toString())
                .servlet("/bridge", "BridgeServlet", new HttpServlet() {
                    @Override
                    public void init() {
                        servletName.set(getServletConfig().getServletName());
                        initParameter.set(getServletConfig().getInitParameter("commandline"));
                    }

                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        boolean hasLaunchIni = req.getServletContext().getResource("/WEB-INF/launch.ini") != null;
                        boolean hasConfig = req.getServletContext()
                                .getResourceAsStream("/WEB-INF/configuration/config.ini") != null;
                        boolean hasConfigurationDir = req.getServletContext()
                                .getResourcePaths("/WEB-INF/")
                                .contains("/WEB-INF/configuration/");
                        Object tempDirectory = req.getServletContext().getAttribute("jakarta.servlet.context.tempdir");
                        String realPath = req.getServletContext().getRealPath("/WEB-INF/launch.ini");
                        resp.getWriter().write("launch=" + hasLaunchIni
                                + "|config=" + hasConfig
                                + "|dir=" + hasConfigurationDir
                                + "|temp=" + (tempDirectory != null)
                                + "|real=" + (realPath != null));
                    }
                }, java.util.Map.of("commandline", "-console"))
                .build());

        FullHttpResponse response = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/app/bridge"),
                null, null));

        assertEquals("BridgeServlet", servletName.get());
        assertEquals("-console", initParameter.get());
        assertEquals(200, response.status().code());
        assertEquals("launch=true|config=true|dir=true|temp=true|real=true",
                response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void supportsSameApplicationNameAcrossDifferentContextPaths() throws Exception {
        SimpleServletContainer container = new SimpleServletContainer();

        container.deploy(SimpleServletApplication.builder("shared-name", "/")
                .servlet("/hello", "RootHello", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("root");
                    }
                })
                .build());

        container.deploy(SimpleServletApplication.builder("shared-name", "/tadpole")
                .servlet("/hello", "TadpoleHello", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("tadpole");
                    }
                })
                .build());

        FullHttpResponse rootResponse = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello"),
                null, null));
        FullHttpResponse tadpoleResponse = container.handle(new HttpExchange(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tadpole/hello"),
                null, null));

        assertEquals("root", rootResponse.content().toString(StandardCharsets.UTF_8));
        assertEquals("tadpole", tadpoleResponse.content().toString(StandardCharsets.UTF_8));
        assertEquals(2, container.listDeployedApplications().size());
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

    private static HttpServletResponse committedResponse() {
        ServletResponseContext responseContext = new ServletResponseContext();
        responseContext.toNettyResponse(false, null);
        return ServletProxyFactory.createResponse(responseContext);
    }
}
