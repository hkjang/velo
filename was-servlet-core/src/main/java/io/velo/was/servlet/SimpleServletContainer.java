package io.velo.was.servlet;

import io.netty.handler.codec.http.FullHttpResponse;
import io.velo.was.http.HttpExchange;
import io.velo.was.http.HttpResponses;
import io.velo.was.http.ResponseSink;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SimpleServletContainer implements ServletContainer, AutoCloseable {

    private final InMemoryHttpSessionStore sessionStore;
    private final SessionExpirationScheduler sessionScheduler;
    private final int defaultSessionTimeoutSeconds;
    private final Map<String, DeployedApplication> applications = new ConcurrentHashMap<>();
    private final Map<String, Object> serverAttributes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService asyncExecutor =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(),
                    Thread.ofVirtual().name("velo-async-", 0).factory());

    public SimpleServletContainer() {
        this(60, 1800);
    }

    /**
     * @param sessionPurgeIntervalSeconds interval between session expiration scans
     */
    public SimpleServletContainer(int sessionPurgeIntervalSeconds) {
        this(sessionPurgeIntervalSeconds, 1800);
    }

    /**
     * @param sessionPurgeIntervalSeconds interval between session expiration scans
     * @param defaultSessionTimeoutSeconds default session timeout in seconds
     */
    public SimpleServletContainer(int sessionPurgeIntervalSeconds, int defaultSessionTimeoutSeconds) {
        this.defaultSessionTimeoutSeconds = defaultSessionTimeoutSeconds;
        this.sessionStore = new InMemoryHttpSessionStore(defaultSessionTimeoutSeconds);
        this.sessionScheduler = new SessionExpirationScheduler(sessionStore, sessionPurgeIntervalSeconds);
        this.sessionScheduler.start();
    }

    /** Returns the session store (for testing and monitoring). */
    public InMemoryHttpSessionStore sessionStore() {
        return sessionStore;
    }

    /** Returns summaries of all deployed applications (for admin/monitoring). */
    public List<DeployedAppInfo> listDeployedApplications() {
        return applications.values().stream()
                .map(app -> new DeployedAppInfo(app.name(), app.contextPath(),
                        app.servlets().size(), app.filters().size()))
                .toList();
    }

    /**
     * Sets a global server-level attribute accessible to all applications.
     */
    public void setServerAttribute(String name, Object value) {
        if (value == null) {
            serverAttributes.remove(name);
        } else {
            serverAttributes.put(name, value);
        }
    }

    /** Read-only info record for deployed applications. */
    public record DeployedAppInfo(String name, String contextPath, int servletCount, int filterCount) {}

    @Override
    public void close() {
        sessionScheduler.close();
        asyncExecutor.shutdown();
    }

    @Override
    public void deploy(ServletApplication application) throws Exception {
        ServletContext servletContext = ServletProxyFactory.createServletContext(
                application.contextPath(),
                application.name(),
                application.classLoader(),
                application.initParameters(),
                serverAttributes,
                new ServletProxyFactory.RequestDispatcherResolver() {
                    @Override
                    public void forward(String path, jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) throws Exception {
                        dispatchFromProxy(application.contextPath(), path, request, response, DispatcherType.FORWARD);
                    }

                    @Override
                    public void include(String path, jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) throws Exception {
                        dispatchFromProxy(application.contextPath(), path, request, response, DispatcherType.INCLUDE);
                    }
                });
        ServletContextEvent servletContextEvent = new ServletContextEvent(servletContext);

        List<FilterRuntime> filterRuntimes = new ArrayList<>();
        for (int index = 0; index < application.filters().size(); index++) {
            FilterRegistrationSpec registration = application.filters().get(index);
            Filter filter = registration.filter();
            FilterConfig filterConfig = ServletProxyFactory.createFilterConfig(
                    "filter-" + index,
                    servletContext,
                    application.initParameters());
            filter.init(filterConfig);
            filterRuntimes.add(new FilterRuntime(registration, filter));
        }

        Map<String, ServletRuntime> servletRuntimes = new LinkedHashMap<>();
        for (Map.Entry<String, Servlet> entry : application.servlets().entrySet()) {
            ServletConfig config = ServletProxyFactory.createServletConfig(entry.getKey(), servletContext, application.initParameters());
            entry.getValue().init(config);
            servletRuntimes.put(entry.getKey(), new ServletRuntime(entry.getKey(), entry.getValue()));
        }

        List<ServletContextListener> servletContextListeners = List.copyOf(application.servletContextListeners());
        for (ServletContextListener listener : servletContextListeners) {
            listener.contextInitialized(servletContextEvent);
        }

        applications.put(application.name(), new DeployedApplication(
                application.name(),
                application.contextPath(),
                servletContext,
                servletRuntimes,
                filterRuntimes,
                servletContextListeners,
                List.copyOf(application.servletRequestListeners()),
                List.copyOf(application.welcomeFiles())));
    }

    @Override
    public void undeploy(String applicationName) throws Exception {
        DeployedApplication application = applications.remove(applicationName);
        if (application == null) {
            return;
        }
        ServletContextEvent servletContextEvent = new ServletContextEvent(application.servletContext);
        for (ServletRuntime runtime : application.servlets.values()) {
            runtime.servlet.destroy();
        }
        for (FilterRuntime filterRuntime : application.filters) {
            filterRuntime.filter.destroy();
        }
        for (ServletContextListener listener : application.servletContextListeners) {
            listener.contextDestroyed(servletContextEvent);
        }
    }

    @Override
    public FullHttpResponse handle(HttpExchange exchange) {
        DeployedApplication application = resolveApplication(exchange.path());
        if (application == null) {
            return HttpResponses.notFound("No servlet application for " + exchange.path());
        }

        String requestPath = exchange.path();

        // Welcome file resolution: if request path is "/" or ends with "/" and no exact servlet mapping,
        // try welcome files in order (e.g., index.jsp, index.html)
        String effectivePath = requestPath;
        String applicationRelative = application.contextPath.isEmpty() ? requestPath : requestPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        if ("/".equals(applicationRelative) || applicationRelative.endsWith("/")) {
            String welcomeResolved = resolveWelcomeFile(application, applicationRelative);
            if (welcomeResolved != null) {
                effectivePath = application.contextPath + welcomeResolved;
                applicationRelative = welcomeResolved;
            }
        }

        ServletRuntime runtime = application.resolveServlet(effectivePath);
        if (runtime == null) {
            return HttpResponses.notFound("No servlet mapping for " + exchange.path());
        }
        String servletPath = resolveServletPath(applicationRelative, runtime.mapping);
        String pathInfo = resolvePathInfo(applicationRelative, runtime.mapping);

        SessionState initialSessionState = resolveSession(exchange);
        ServletRequestContext requestContext = new ServletRequestContext(
                exchange,
                application.servletContext,
                application.contextPath,
                servletPath,
                pathInfo,
                initialSessionState);
        ServletResponseContext responseContext = new ServletResponseContext();

        try {
            DispatchResult dispatchResult = dispatch(
                    application,
                    runtime,
                    requestContext,
                    responseContext,
                    new SessionHolder(requestContext, application.servletContext),
                    exchange.request().method().name(),
                    exchange.responseSink(),
                    true);
            if (dispatchResult.asyncStarted()) {
                return null;
            }
            return responseContext.toNettyResponse(
                    "HEAD".equals(exchange.request().method().name()),
                    dispatchResult.sessionHolder.created(),
                    dispatchResult.sessionHolder.sessionId());
        } catch (Exception exception) {
            return HttpResponses.serverError("Servlet execution failed: " + exception.getMessage());
        }
    }

    private void dispatchFromProxy(String contextPath,
                                   String targetPath,
                                   jakarta.servlet.ServletRequest request,
                                   jakarta.servlet.ServletResponse response,
                                   DispatcherType dispatcherType) throws Exception {
        InternalRequestBridge requestBridge = (InternalRequestBridge) request;
        InternalResponseBridge responseBridge = (InternalResponseBridge) response;
        ServletRequestContext sourceRequest = requestBridge.requestContext();
        ServletResponseContext sourceResponse = responseBridge.responseContext();

        String absolutePath = normalizeDispatchPath(contextPath, targetPath);
        String dispatchPath = stripQueryString(absolutePath);
        String dispatchQueryString = extractQueryString(absolutePath);
        DeployedApplication application = resolveApplication(dispatchPath);
        if (application == null) {
            throw new jakarta.servlet.ServletException("No application for dispatcher path " + dispatchPath);
        }
        ServletRuntime runtime = application.resolveServlet(dispatchPath);
        if (runtime == null) {
            throw new jakarta.servlet.ServletException("No servlet for dispatcher path " + dispatchPath);
        }

        if (dispatcherType == DispatcherType.FORWARD) {
            sourceResponse.reset();
        }

        String applicationRelative = application.contextPath.isEmpty() ? dispatchPath : dispatchPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        String servletPath = resolveServletPath(applicationRelative, runtime.mapping);
        String pathInfo = resolvePathInfo(applicationRelative, runtime.mapping);
        if (dispatcherType == DispatcherType.FORWARD) {
            applyForwardAttributes(sourceRequest);
        } else if (dispatcherType == DispatcherType.INCLUDE) {
            applyIncludeAttributes(sourceRequest, dispatchPath, application.contextPath, servletPath, pathInfo, dispatchQueryString);
        }

        String effectiveRequestUri = dispatcherType == DispatcherType.FORWARD ? dispatchPath : sourceRequest.requestUri();
        String effectiveQueryString = dispatcherType == DispatcherType.FORWARD ? dispatchQueryString : sourceRequest.queryString();

        ServletRequestContext dispatchRequest = sourceRequest.forDispatch(
                effectiveRequestUri,
                effectiveQueryString,
                servletPath,
                pathInfo,
                dispatcherType);
        dispatch(application,
                runtime,
                dispatchRequest,
                sourceResponse,
                new SessionHolder(dispatchRequest, application.servletContext, sourceRequest.sessionState()),
                sourceRequest.request().method().name(),
                null,
                false);
    }

    private void asyncDispatchFromContext(String contextPath,
                                         String targetPath,
                                         ServletRequest request,
                                         ServletResponse response) throws Exception {
        InternalRequestBridge requestBridge = (InternalRequestBridge) request;
        InternalResponseBridge responseBridge = (InternalResponseBridge) response;
        ServletRequestContext sourceRequest = requestBridge.requestContext();
        ServletResponseContext sourceResponse = responseBridge.responseContext();

        String absolutePath = normalizeDispatchPath(contextPath, targetPath);
        String dispatchPath = stripQueryString(absolutePath);
        String dispatchQueryString = extractQueryString(absolutePath);
        DeployedApplication application = resolveApplication(dispatchPath);
        if (application == null) {
            throw new jakarta.servlet.ServletException("No application for async dispatch path " + dispatchPath);
        }
        ServletRuntime runtime = application.resolveServlet(dispatchPath);
        if (runtime == null) {
            throw new jakarta.servlet.ServletException("No servlet for async dispatch path " + dispatchPath);
        }

        String applicationRelative = application.contextPath.isEmpty() ? dispatchPath : dispatchPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        String servletPath = resolveServletPath(applicationRelative, runtime.mapping);
        String pathInfo = resolvePathInfo(applicationRelative, runtime.mapping);

        ServletRequestContext dispatchRequest = sourceRequest.forDispatch(
                dispatchPath,
                dispatchQueryString,
                servletPath,
                pathInfo,
                DispatcherType.ASYNC);
        dispatch(application,
                runtime,
                dispatchRequest,
                sourceResponse,
                new SessionHolder(dispatchRequest, application.servletContext, sourceRequest.sessionState()),
                sourceRequest.request().method().name(),
                null,
                false);
    }

    private DispatchResult dispatch(DeployedApplication application,
                                    ServletRuntime runtime,
                                    ServletRequestContext requestContext,
                                    ServletResponseContext responseContext,
                                    SessionHolder sessionHolder,
                                    String httpMethod,
                                    ResponseSink responseSink,
                                    boolean invokeRequestListeners) throws Exception {
        AsyncHolder asyncHolder = new AsyncHolder();
        HttpServletResponse response = ServletProxyFactory.createResponse(responseContext);
        asyncHolder.response(response);

        HttpServletRequest request = ServletProxyFactory.createRequest(
                requestContext,
                sessionHolder::session,
                new ServletProxyFactory.RequestDispatcherResolver() {
                    @Override
                    public void forward(String path, jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse) throws Exception {
                        dispatchFromProxy(application.contextPath, path, servletRequest, servletResponse, DispatcherType.FORWARD);
                    }

                    @Override
                    public void include(String path, jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse) throws Exception {
                        dispatchFromProxy(application.contextPath, path, servletRequest, servletResponse, DispatcherType.INCLUDE);
                    }
                },
                new ServletProxyFactory.AsyncContextAccessor() {
                    @Override
                    public AsyncContext startAsync(ServletRequest req, ServletResponse res) {
                        VeloAsyncContext.SessionHolder asyncSessionHolder = new VeloAsyncContext.SessionHolder() {
                            @Override
                            public boolean created() {
                                return sessionHolder.created();
                            }

                            @Override
                            public String sessionId() {
                                return sessionHolder.sessionId();
                            }
                        };
                        VeloAsyncContext asyncContext = new VeloAsyncContext(
                                req != null ? req : asyncHolder.request(),
                                res != null ? res : asyncHolder.response(),
                                responseContext,
                                asyncSessionHolder,
                                httpMethod,
                                responseSink,
                                (path, asyncReq, asyncRes) ->
                                        asyncDispatchFromContext(application.contextPath, path, asyncReq, asyncRes),
                                asyncExecutor);
                        asyncHolder.start(asyncContext);
                        return asyncContext;
                    }

                    @Override
                    public boolean isAsyncStarted() {
                        return asyncHolder.isStarted();
                    }

                    @Override
                    public boolean isAsyncSupported() {
                        return responseSink != null;
                    }

                    @Override
                    public AsyncContext getAsyncContext() {
                        return asyncHolder.context();
                    }
                });
        asyncHolder.request(request);
        ServletRequestEvent requestEvent = ServletProxyFactory.createServletRequestEvent(application.servletContext, request);

        try {
            if (invokeRequestListeners) {
                for (ServletRequestListener listener : application.servletRequestListeners) {
                    listener.requestInitialized(requestEvent);
                }
            }
            new SimpleFilterChain(application.resolveFilters(requestContext), runtime.servlet).doFilter(request, response);
            return new DispatchResult(sessionHolder, asyncHolder.isStarted());
        } finally {
            if (invokeRequestListeners && !asyncHolder.isStarted()) {
                for (int index = application.servletRequestListeners.size() - 1; index >= 0; index--) {
                    application.servletRequestListeners.get(index).requestDestroyed(requestEvent);
                }
            }
        }
    }

    private String normalizeDispatchPath(String contextPath, String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            return contextPath.isEmpty() ? "/" : contextPath;
        }
        if (targetPath.startsWith("/")) {
            return contextPath + targetPath;
        }
        return contextPath + "/" + targetPath;
    }

    private void applyForwardAttributes(ServletRequestContext sourceRequest) {
        if (sourceRequest.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null) {
            return;
        }
        sourceRequest.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, sourceRequest.requestUri());
        sourceRequest.setAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH, sourceRequest.contextPath());
        sourceRequest.setAttribute(RequestDispatcher.FORWARD_SERVLET_PATH, sourceRequest.servletPath());
        sourceRequest.setAttribute(RequestDispatcher.FORWARD_PATH_INFO, sourceRequest.pathInfo());
        sourceRequest.setAttribute(RequestDispatcher.FORWARD_QUERY_STRING, sourceRequest.queryString());
    }

    private void applyIncludeAttributes(ServletRequestContext sourceRequest,
                                        String requestUri,
                                        String contextPath,
                                        String servletPath,
                                        String pathInfo,
                                        String queryString) {
        sourceRequest.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI, requestUri);
        sourceRequest.setAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH, contextPath);
        sourceRequest.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, servletPath);
        sourceRequest.setAttribute(RequestDispatcher.INCLUDE_PATH_INFO, pathInfo);
        sourceRequest.setAttribute(RequestDispatcher.INCLUDE_QUERY_STRING, queryString);
    }

    private String stripQueryString(String path) {
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
    }

    private String extractQueryString(String path) {
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 && queryIndex < path.length() - 1 ? path.substring(queryIndex + 1) : null;
    }

    private DeployedApplication resolveApplication(String requestPath) {
        return applications.values().stream()
                .sorted(Comparator.comparingInt((DeployedApplication app) -> app.contextPath.length()).reversed())
                .filter(app -> app.matches(requestPath))
                .findFirst()
                .orElse(null);
    }

    private SessionState resolveSession(HttpExchange exchange) {
        String cookieHeader = exchange.headers().get("Cookie");
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String cookiePart : cookieHeader.split(";")) {
            String[] pair = cookiePart.trim().split("=", 2);
            if (pair.length == 2 && "JSESSIONID".equals(pair[0].trim())) {
                SessionState sessionState = sessionStore.find(pair[1].trim());
                if (sessionState != null && sessionState.isValid()) {
                    sessionState.touch();
                    return sessionState;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a welcome file for directory-like requests (e.g., "/" or "/subdir/").
     * Iterates through the application's welcome-file-list and returns the first
     * welcome file path that has a matching servlet mapping.
     *
     * @return the resolved welcome file path (e.g., "/index.jsp") or null if none found
     */
    private String resolveWelcomeFile(DeployedApplication application, String directoryPath) {
        if (application.welcomeFiles.isEmpty()) {
            return null;
        }
        String base = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
        for (String welcomeFile : application.welcomeFiles) {
            String candidate = base + welcomeFile;
            // Check if there's a servlet mapping that can handle this welcome file
            String testPath = application.contextPath + candidate;
            ServletRuntime matched = application.resolveServlet(testPath);
            if (matched != null) {
                return candidate;
            }
        }
        return null;
    }

    private String resolveServletPath(String applicationRelative, String mapping) {
        if ("/".equals(mapping) || mapping.startsWith("*.")) {
            return applicationRelative;
        }
        // Path-prefix mapping: /path/* → servletPath = /path
        if (mapping.endsWith("/*")) {
            return mapping.substring(0, mapping.length() - 2);
        }
        return mapping;
    }

    private String resolvePathInfo(String applicationRelative, String mapping) {
        if ("/".equals(mapping) || mapping.startsWith("*.")) {
            return null;
        }
        // Path-prefix mapping: /path/* → pathInfo = remainder after prefix
        if (mapping.endsWith("/*")) {
            String prefix = mapping.substring(0, mapping.length() - 2);
            if (applicationRelative.equals(prefix)) {
                return null;
            }
            return applicationRelative.substring(prefix.length());
        }
        if (applicationRelative.equals(mapping)) {
            return null;
        }
        return applicationRelative.substring(mapping.length());
    }

    private record DeployedApplication(
            String name,
            String contextPath,
            ServletContext servletContext,
            Map<String, ServletRuntime> servlets,
            List<FilterRuntime> filters,
            List<ServletContextListener> servletContextListeners,
            List<ServletRequestListener> servletRequestListeners,
            List<String> welcomeFiles
    ) {
        private boolean matches(String requestPath) {
            if (contextPath.isEmpty()) {
                return true;
            }
            return requestPath.equals(contextPath) || requestPath.startsWith(contextPath + "/");
        }

        private ServletRuntime resolveServlet(String requestPath) {
            String applicationRelative = contextPath.isEmpty() ? requestPath : requestPath.substring(contextPath.length());
            if (applicationRelative.isEmpty()) {
                applicationRelative = "/";
            }
            String resolvedPath = applicationRelative;

            return servlets.values().stream()
                    .sorted(Comparator.comparingInt((ServletRuntime runtime) -> runtime.mapping.length()).reversed())
                    .filter(runtime -> runtime.matches(resolvedPath))
                    .findFirst()
                    .orElse(null);
        }

        private List<Filter> resolveFilters(ServletRequestContext requestContext) {
            String applicationRelativePath = requestContext.servletPath();
            if (requestContext.pathInfo() != null) {
                applicationRelativePath = applicationRelativePath + requestContext.pathInfo();
            }
            String effectivePath = applicationRelativePath == null || applicationRelativePath.isBlank() ? "/" : applicationRelativePath;
            return filters.stream()
                    .filter(runtime -> runtime.registration.matches(effectivePath, requestContext.dispatcherType()))
                    .map(runtime -> runtime.filter)
                    .toList();
        }
    }

    private static class ServletRuntime {
        private final String mapping;
        private final Servlet servlet;

        private ServletRuntime(String mapping, Servlet servlet) {
            this.mapping = mapping;
            this.servlet = servlet;
        }

        private boolean matches(String applicationRelative) {
            if ("/".equals(mapping)) {
                return true;
            }
            // Extension mapping: *.ext
            if (mapping.startsWith("*.")) {
                String extension = mapping.substring(1); // ".jsp"
                return applicationRelative.endsWith(extension);
            }
            // Path-prefix mapping: /path/*
            if (mapping.endsWith("/*")) {
                String prefix = mapping.substring(0, mapping.length() - 2);
                return applicationRelative.equals(prefix) || applicationRelative.startsWith(prefix + "/");
            }
            return applicationRelative.equals(mapping) || applicationRelative.startsWith(mapping + "/");
        }

        private void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
            servlet.service(request, response);
        }
    }

    private static class FilterRuntime {
        private final FilterRegistrationSpec registration;
        private final Filter filter;

        private FilterRuntime(FilterRegistrationSpec registration, Filter filter) {
            this.registration = registration;
            this.filter = filter;
        }
    }

    private record DispatchResult(SessionHolder sessionHolder, boolean asyncStarted) {
    }

    private static class AsyncHolder {
        private volatile VeloAsyncContext context;
        private volatile HttpServletRequest request;
        private volatile HttpServletResponse response;

        void start(VeloAsyncContext ctx) {
            this.context = ctx;
        }

        boolean isStarted() {
            return context != null;
        }

        VeloAsyncContext context() {
            return context;
        }

        void request(HttpServletRequest request) {
            this.request = request;
        }

        HttpServletRequest request() {
            return request;
        }

        void response(HttpServletResponse response) {
            this.response = response;
        }

        HttpServletResponse response() {
            return response;
        }
    }

    private final class SessionHolder {
        private final ServletRequestContext requestContext;
        private final ServletContext servletContext;
        private jakarta.servlet.http.HttpSession session;
        private boolean created;

        private SessionHolder(ServletRequestContext requestContext, ServletContext servletContext) {
            this(requestContext, servletContext, requestContext.sessionState());
        }

        private SessionHolder(ServletRequestContext requestContext, ServletContext servletContext, SessionState existingSessionState) {
            this.requestContext = requestContext;
            this.servletContext = servletContext;
            if (existingSessionState != null) {
                this.session = ServletProxyFactory.createSessionProxy(
                        existingSessionState,
                        sessionStore,
                        servletContext,
                        false);
            }
        }

        private jakarta.servlet.http.HttpSession session(boolean create) {
            if (session != null) {
                return session;
            }
            if (!create) {
                return null;
            }
            SessionState sessionState = sessionStore.create();
            requestContext.sessionState(sessionState, true);
            session = ServletProxyFactory.createSessionProxy(sessionState, sessionStore, servletContext, true);
            created = true;
            return session;
        }

        private boolean created() {
            return created;
        }

        private String sessionId() {
            return session == null ? null : session.getId();
        }
    }
}
