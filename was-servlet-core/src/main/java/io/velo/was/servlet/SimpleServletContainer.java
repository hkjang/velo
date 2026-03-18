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
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SimpleServletContainer implements ServletContainer, AutoCloseable {

    private final HttpSessionStore sessionStore;
    private final SessionExpirationScheduler sessionScheduler;
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
        this(new InMemoryHttpSessionStore(defaultSessionTimeoutSeconds), sessionPurgeIntervalSeconds);
    }

    public SimpleServletContainer(HttpSessionStore sessionStore) {
        this(sessionStore, 60);
    }

    public SimpleServletContainer(HttpSessionStore sessionStore, int sessionPurgeIntervalSeconds) {
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sessionScheduler = new SessionExpirationScheduler(this.sessionStore, sessionPurgeIntervalSeconds);
        this.sessionScheduler.start();
    }

    /** Returns the session store (for testing and monitoring). */
    public HttpSessionStore sessionStore() {
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
        sessionStore.close();
        asyncExecutor.shutdown();
    }

    @Override
    public void deploy(ServletApplication application) throws Exception {
        List<ServletContextAttributeListener> servletContextAttributeListeners =
                List.copyOf(application.servletContextAttributeListeners());
        ServletContext servletContext = ServletProxyFactory.createServletContext(
                application.contextPath(),
                application.name(),
                application.classLoader(),
                application.initParameters(),
                serverAttributes,
                servletContextAttributeListeners,
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
            Map<String, String> servletInitParameters = new LinkedHashMap<>(application.initParameters());
            servletInitParameters.putAll(application.servletInitParameters(entry.getKey()));
            ServletConfig config = ServletProxyFactory.createServletConfig(
                    application.servletName(entry.getKey()),
                    servletContext,
                    servletInitParameters);
            entry.getValue().init(config);
            servletRuntimes.put(entry.getKey(), new ServletRuntime(entry.getKey(), entry.getValue()));
        }

        List<ServletContextListener> servletContextListeners = List.copyOf(application.servletContextListeners());
        List<ServletRequestListener> servletRequestListeners = List.copyOf(application.servletRequestListeners());
        List<HttpSessionListener> httpSessionListeners = List.copyOf(application.httpSessionListeners());
        List<HttpSessionAttributeListener> httpSessionAttributeListeners =
                List.copyOf(application.httpSessionAttributeListeners());
        List<HttpSessionIdListener> httpSessionIdListeners = List.copyOf(application.httpSessionIdListeners());
        for (ServletContextListener listener : servletContextListeners) {
            listener.contextInitialized(servletContextEvent);
        }

        DeployedApplication deployedApplication = new DeployedApplication(
                application.name(),
                application.contextPath(),
                servletContext,
                servletRuntimes,
                filterRuntimes,
                servletContextListeners,
                servletContextAttributeListeners,
                servletRequestListeners,
                httpSessionListeners,
                httpSessionAttributeListeners,
                httpSessionIdListeners,
                List.copyOf(application.errorPages()),
                List.copyOf(application.welcomeFiles()));
        DeployedApplication previous = applications.put(applicationKey(application.contextPath()), deployedApplication);
        if (previous != null) {
            destroyApplication(previous);
        }
    }

    @Override
    public void undeploy(String applicationName) throws Exception {
        DeployedApplication application = applications.remove(applicationKey(applicationName));
        if (application == null) {
            String normalizedContextPath = normalizeContextPath(applicationName);
            for (Map.Entry<String, DeployedApplication> entry : new ArrayList<>(applications.entrySet())) {
                DeployedApplication candidate = entry.getValue();
                if (candidate.name().equals(applicationName) || candidate.contextPath().equals(normalizedContextPath)) {
                    if (applications.remove(entry.getKey(), candidate)) {
                        application = candidate;
                        break;
                    }
                }
            }
        }
        if (application == null) {
            return;
        }
        destroyApplication(application);
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
        SessionHolder sessionHolder = new SessionHolder(application, requestContext, application.servletContext);

        try {
            DispatchResult dispatchResult = dispatch(
                    application,
                    runtime,
                    requestContext,
                    responseContext,
                    sessionHolder,
                    exchange.request().method().name(),
                    exchange.responseSink(),
                    true);
            if (dispatchResult.asyncStarted()) {
                return null;
            }
            if (responseContext.errorSent()) {
                DispatchResult errorDispatchResult = dispatchErrorPage(
                        application,
                        runtime.mapping,
                        requestContext,
                        responseContext,
                        sessionHolder,
                        responseSinkFor(exchange),
                        exchange.request().method().name(),
                        responseContext.status(),
                        responseContext.errorMessage(),
                        null);
                if (errorDispatchResult != null && errorDispatchResult.asyncStarted()) {
                    return null;
                }
            }
            return responseContext.toNettyResponse(
                    "HEAD".equals(exchange.request().method().name()),
                    dispatchResult.sessionHolder.shouldSetCookie(),
                    dispatchResult.sessionHolder.sessionId());
        } catch (Exception exception) {
            try {
                DispatchResult errorDispatchResult = dispatchErrorPage(
                        application,
                        runtime.mapping,
                        requestContext,
                        responseContext,
                        sessionHolder,
                        responseSinkFor(exchange),
                        exchange.request().method().name(),
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        exception.getMessage(),
                        exception);
                if (errorDispatchResult != null) {
                    if (errorDispatchResult.asyncStarted()) {
                        return null;
                    }
                    return responseContext.toNettyResponse(
                            "HEAD".equals(exchange.request().method().name()),
                            sessionHolder.shouldSetCookie(),
                            sessionHolder.sessionId());
                }
            } catch (Exception ignored) {
                // Fall back to plain 500 response when error-page dispatch fails.
            }
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
                new SessionHolder(application, dispatchRequest, application.servletContext, sourceRequest.sessionState()),
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
                new SessionHolder(application, dispatchRequest, application.servletContext, sourceRequest.sessionState()),
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
                new ServletProxyFactory.SessionAccessor() {
                    @Override
                    public HttpSession session(boolean create) {
                        return sessionHolder.session(create);
                    }

                    @Override
                    public String changeSessionId() {
                        return sessionHolder.changeSessionId();
                    }
                },
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
                            public boolean shouldSetCookie() {
                                return sessionHolder.shouldSetCookie();
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

    private ResponseSink responseSinkFor(HttpExchange exchange) {
        return exchange.responseSink();
    }

    private DispatchResult dispatchErrorPage(DeployedApplication application,
                                             String servletName,
                                             ServletRequestContext sourceRequest,
                                             ServletResponseContext responseContext,
                                             SessionHolder sessionHolder,
                                             ResponseSink responseSink,
                                             String httpMethod,
                                             int statusCode,
                                             String message,
                                             Throwable throwable) throws Exception {
        if (sourceRequest.dispatcherType() == DispatcherType.ERROR || responseContext.isCommitted()) {
            return null;
        }
        ErrorPageSpec errorPage = application.resolveErrorPage(throwable, statusCode);
        if (errorPage == null) {
            return null;
        }

        String dispatchPath = application.contextPath + errorPage.location();
        ServletRuntime errorRuntime = application.resolveServlet(dispatchPath);
        if (errorRuntime == null) {
            return null;
        }

        responseContext.reset();
        responseContext.setStatus(statusCode);
        populateErrorAttributes(sourceRequest, servletName, statusCode, message, throwable);

        String applicationRelative = application.contextPath.isEmpty()
                ? dispatchPath
                : dispatchPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        String servletPath = resolveServletPath(applicationRelative, errorRuntime.mapping);
        String pathInfo = resolvePathInfo(applicationRelative, errorRuntime.mapping);

        ServletRequestContext errorRequest = sourceRequest.forDispatch(
                dispatchPath,
                null,
                servletPath,
                pathInfo,
                DispatcherType.ERROR);
        return dispatch(application,
                errorRuntime,
                errorRequest,
                responseContext,
                sessionHolder,
                httpMethod,
                responseSink,
                false);
    }

    private void populateErrorAttributes(ServletRequestContext requestContext,
                                         String servletName,
                                         int statusCode,
                                         String message,
                                         Throwable throwable) {
        requestContext.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, statusCode);
        requestContext.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
        requestContext.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, requestContext.requestUri());
        requestContext.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, servletName);
        requestContext.setAttribute(RequestDispatcher.ERROR_EXCEPTION, throwable);
        requestContext.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE,
                throwable == null ? null : throwable.getClass());
    }

    private DeployedApplication resolveApplication(String requestPath) {
        return applications.values().stream()
                .sorted(Comparator.comparingInt((DeployedApplication app) -> app.contextPath.length()).reversed())
                .filter(app -> app.matches(requestPath))
                .findFirst()
                .orElse(null);
    }

    private void destroyApplication(DeployedApplication application) throws Exception {
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

    private String applicationKey(String contextPath) {
        return normalizeContextPath(contextPath);
    }

    private String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
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
            List<ServletContextAttributeListener> servletContextAttributeListeners,
            List<ServletRequestListener> servletRequestListeners,
            List<HttpSessionListener> httpSessionListeners,
            List<HttpSessionAttributeListener> httpSessionAttributeListeners,
            List<HttpSessionIdListener> httpSessionIdListeners,
            List<ErrorPageSpec> errorPages,
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

        private ErrorPageSpec resolveErrorPage(Throwable throwable, int statusCode) {
            ErrorPageSpec exceptionMatch = errorPages.stream()
                    .filter(spec -> spec.matchesException(throwable))
                    .min(Comparator.comparingInt(spec -> inheritanceDistance(throwable.getClass(), spec.exceptionType())))
                    .orElse(null);
            if (exceptionMatch != null) {
                return exceptionMatch;
            }
            return errorPages.stream()
                    .filter(spec -> spec.matchesStatus(statusCode))
                    .findFirst()
                    .orElse(null);
        }

        private int inheritanceDistance(Class<?> candidate, Class<?> target) {
            int distance = 0;
            Class<?> current = candidate;
            while (current != null && !current.equals(target)) {
                current = current.getSuperclass();
                distance++;
            }
            return distance;
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
        private final DeployedApplication application;
        private final ServletRequestContext requestContext;
        private final ServletContext servletContext;
        private HttpSession session;
        private boolean created;
        private boolean sessionCookieUpdated;

        private SessionHolder(DeployedApplication application, ServletRequestContext requestContext, ServletContext servletContext) {
            this(application, requestContext, servletContext, requestContext.sessionState());
        }

        private SessionHolder(DeployedApplication application,
                              ServletRequestContext requestContext,
                              ServletContext servletContext,
                              SessionState existingSessionState) {
            this.application = application;
            this.requestContext = requestContext;
            this.servletContext = servletContext;
            if (existingSessionState != null) {
                existingSessionState.setNotifier(sessionNotifier());
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
            sessionState.setNotifier(sessionNotifier());
            session = ServletProxyFactory.createSessionProxy(sessionState, sessionStore, servletContext, true);
            created = true;
            sessionState.fireSessionCreated();
            return session;
        }

        private String changeSessionId() {
            SessionState sessionState = requestContext.sessionState();
            if (sessionState == null || !sessionState.isValid()) {
                throw new IllegalStateException("No session associated with the current request");
            }
            String oldSessionId = sessionState.getId();
            String newSessionId = sessionStore.changeSessionId(sessionState);
            requestContext.sessionState(sessionState, false);
            sessionCookieUpdated = true;
            sessionState.fireSessionIdChanged(oldSessionId);
            return newSessionId;
        }

        private boolean shouldSetCookie() {
            return created || sessionCookieUpdated;
        }

        private String sessionId() {
            return session == null ? null : session.getId();
        }

        private SessionState.SessionNotifier sessionNotifier() {
            return new SessionState.SessionNotifier() {
                @Override
                public void sessionCreated(SessionState state) {
                    HttpSessionEvent event = new HttpSessionEvent(asSession(state, true));
                    for (HttpSessionListener listener : application.httpSessionListeners) {
                        listener.sessionCreated(event);
                    }
                }

                @Override
                public void sessionDestroyed(SessionState state) {
                    HttpSessionEvent event = new HttpSessionEvent(asSession(state, false));
                    for (int index = application.httpSessionListeners.size() - 1; index >= 0; index--) {
                        application.httpSessionListeners.get(index).sessionDestroyed(event);
                    }
                }

                @Override
                public void sessionIdChanged(SessionState state, String oldSessionId) {
                    HttpSessionEvent event = new HttpSessionEvent(asSession(state, false));
                    for (HttpSessionIdListener listener : application.httpSessionIdListeners) {
                        listener.sessionIdChanged(event, oldSessionId);
                    }
                }

                @Override
                public void attributeAdded(SessionState state, String name, Object value) {
                    HttpSessionBindingEvent event = new HttpSessionBindingEvent(asSession(state, false), name, value);
                    for (HttpSessionAttributeListener listener : application.httpSessionAttributeListeners) {
                        listener.attributeAdded(event);
                    }
                }

                @Override
                public void attributeRemoved(SessionState state, String name, Object value) {
                    HttpSessionBindingEvent event = new HttpSessionBindingEvent(asSession(state, false), name, value);
                    for (HttpSessionAttributeListener listener : application.httpSessionAttributeListeners) {
                        listener.attributeRemoved(event);
                    }
                }

                @Override
                public void attributeReplaced(SessionState state, String name, Object oldValue) {
                    HttpSessionBindingEvent event = new HttpSessionBindingEvent(asSession(state, false), name, oldValue);
                    for (HttpSessionAttributeListener listener : application.httpSessionAttributeListeners) {
                        listener.attributeReplaced(event);
                    }
                }
            };
        }

        private HttpSession asSession(SessionState state, boolean isNew) {
            return ServletProxyFactory.createSessionProxy(state, sessionStore, servletContext, isNew);
        }
    }
}
