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
import jakarta.servlet.http.Cookie;

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

    /**
     * Functional interface for providing global filters that are injected into every
     * deployed application. Used by the MCP gateway to intercept MCP traffic from apps.
     */
    @FunctionalInterface
    public interface GlobalFilterProvider {
        /**
         * Create filters to prepend to the given application's filter chain.
         *
         * @param contextPath the application's context path
         * @param appName     the application's name
         * @return list of filters to prepend, or empty list for none
         */
        List<Filter> createFilters(String contextPath, String appName);
    }

    private final HttpSessionStore sessionStore;
    private final SessionExpirationScheduler sessionScheduler;
    private final SessionCookieSettings sessionCookieSettings;
    private final ServletPathMapper servletPathMapper;
    private final Map<String, DeployedApplication> applications = new ConcurrentHashMap<>();
    private final Map<String, Object> serverAttributes = new ConcurrentHashMap<>();
    private volatile GlobalFilterProvider globalFilterProvider;
    private final ScheduledExecutorService asyncExecutor =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(),
                    Thread.ofVirtual().name("velo-async-", 0).factory());

    public SimpleServletContainer() {
        this(60, 1800, SessionCookieSettings.defaults(), new DefaultServletPathMapper());
    }

    /**
     * @param sessionPurgeIntervalSeconds interval between session expiration scans
     */
    public SimpleServletContainer(int sessionPurgeIntervalSeconds) {
        this(sessionPurgeIntervalSeconds, 1800, SessionCookieSettings.defaults(), new DefaultServletPathMapper());
    }

    /**
     * @param sessionPurgeIntervalSeconds interval between session expiration scans
     * @param defaultSessionTimeoutSeconds default session timeout in seconds
     */
    public SimpleServletContainer(int sessionPurgeIntervalSeconds, int defaultSessionTimeoutSeconds) {
        this(new InMemoryHttpSessionStore(defaultSessionTimeoutSeconds),
                sessionPurgeIntervalSeconds,
                SessionCookieSettings.defaults(),
                new DefaultServletPathMapper());
    }

    public SimpleServletContainer(int sessionPurgeIntervalSeconds,
                                  int defaultSessionTimeoutSeconds,
                                  SessionCookieSettings sessionCookieSettings) {
        this(sessionPurgeIntervalSeconds,
                defaultSessionTimeoutSeconds,
                sessionCookieSettings,
                new DefaultServletPathMapper());
    }

    public SimpleServletContainer(int sessionPurgeIntervalSeconds,
                                  int defaultSessionTimeoutSeconds,
                                  SessionCookieSettings sessionCookieSettings,
                                  ServletPathMapper servletPathMapper) {
        this(new InMemoryHttpSessionStore(defaultSessionTimeoutSeconds),
                sessionPurgeIntervalSeconds,
                sessionCookieSettings,
                servletPathMapper);
    }

    public SimpleServletContainer(HttpSessionStore sessionStore) {
        this(sessionStore, 60, SessionCookieSettings.defaults(), new DefaultServletPathMapper());
    }

    public SimpleServletContainer(HttpSessionStore sessionStore, int sessionPurgeIntervalSeconds) {
        this(sessionStore,
                sessionPurgeIntervalSeconds,
                SessionCookieSettings.defaults(),
                new DefaultServletPathMapper());
    }

    public SimpleServletContainer(HttpSessionStore sessionStore,
                                  int sessionPurgeIntervalSeconds,
                                  SessionCookieSettings sessionCookieSettings) {
        this(sessionStore,
                sessionPurgeIntervalSeconds,
                sessionCookieSettings,
                new DefaultServletPathMapper());
    }

    public SimpleServletContainer(HttpSessionStore sessionStore,
                                  int sessionPurgeIntervalSeconds,
                                  SessionCookieSettings sessionCookieSettings,
                                  ServletPathMapper servletPathMapper) {
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sessionCookieSettings = sessionCookieSettings == null
                ? SessionCookieSettings.defaults()
                : sessionCookieSettings;
        this.servletPathMapper = servletPathMapper == null
                ? new DefaultServletPathMapper()
                : servletPathMapper;
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
     * Set a global filter provider that injects filters into every deployed application.
     * Used by MCP gateway to intercept MCP JSON-RPC traffic from applications.
     */
    public void setGlobalFilterProvider(GlobalFilterProvider provider) {
        this.globalFilterProvider = provider;
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

                    @Override
                    public boolean hasNamedDispatcher(String servletName) {
                        return SimpleServletContainer.this.hasNamedDispatcher(application.contextPath(), servletName);
                    }

                    @Override
                    public void forwardNamed(String servletName,
                                             jakarta.servlet.ServletRequest request,
                                             jakarta.servlet.ServletResponse response) throws Exception {
                        dispatchNamedFromProxy(application.contextPath(), servletName, request, response, DispatcherType.FORWARD);
                    }

                    @Override
                    public void includeNamed(String servletName,
                                             jakarta.servlet.ServletRequest request,
                                             jakarta.servlet.ServletResponse response) throws Exception {
                        dispatchNamedFromProxy(application.contextPath(), servletName, request, response, DispatcherType.INCLUDE);
                    }
                });
        ServletContextEvent servletContextEvent = new ServletContextEvent(servletContext);

        List<FilterRuntime> filterRuntimes = new ArrayList<>();
        for (int index = 0; index < application.filters().size(); index++) {
            FilterRegistrationSpec registration = application.filters().get(index);
            Filter filter = registration.filter();
            Map<String, String> filterInitParameters = new LinkedHashMap<>(application.initParameters());
            filterInitParameters.putAll(registration.initParameters());
            FilterConfig filterConfig = ServletProxyFactory.createFilterConfig(
                    registration.filterName() == null ? "filter-" + index : registration.filterName(),
                    servletContext,
                    filterInitParameters);
            filter.init(filterConfig);
            filterRuntimes.add(new FilterRuntime(registration, filter));
        }

        Map<String, ServletRuntime> servletRuntimes = new LinkedHashMap<>();
        Map<String, ServletRuntime> servletRuntimesByName = new LinkedHashMap<>();
        for (Map.Entry<String, Servlet> entry : application.servlets().entrySet()) {
            Map<String, String> servletInitParameters = new LinkedHashMap<>(application.initParameters());
            servletInitParameters.putAll(application.servletInitParameters(entry.getKey()));
            String servletName = application.servletName(entry.getKey());
            ServletConfig config = ServletProxyFactory.createServletConfig(
                    servletName,
                    servletContext,
                    servletInitParameters);
            entry.getValue().init(config);
            ServletRuntime runtime = new ServletRuntime(entry.getKey(), servletName, entry.getValue());
            servletRuntimes.put(entry.getKey(), runtime);
            servletRuntimesByName.put(servletName, runtime);
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
                servletRuntimesByName,
                this.servletPathMapper,
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

        ResolvedServlet resolvedServlet = application.resolveServlet(effectivePath);
        if (resolvedServlet == null) {
            return HttpResponses.notFound("No servlet mapping for " + exchange.path());
        }
        ServletRuntime runtime = resolvedServlet.runtime();
        String servletPath = resolvedServlet.pathMatch().servletPath();
        String pathInfo = resolvedServlet.pathMatch().pathInfo();

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
                    null,
                    null,
                    null,
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
                    dispatchResult.sessionHolder.sessionCookie());
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
                            sessionHolder.sessionCookie());
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
        InternalRequestBridge requestBridge = DispatchBridgeSupport.requestBridge(request);
        InternalResponseBridge responseBridge = DispatchBridgeSupport.responseBridge(response);
        ServletRequestContext sourceRequest = requestBridge.requestContext();
        ServletResponseContext sourceResponse = responseBridge.responseContext();

        String absolutePath = normalizeDispatchPath(contextPath, targetPath);
        String dispatchPath = stripQueryString(absolutePath);
        String dispatchQueryString = extractQueryString(absolutePath);
        DeployedApplication application = resolveApplication(dispatchPath);
        if (application == null) {
            throw new jakarta.servlet.ServletException("No application for dispatcher path " + dispatchPath);
        }
        ResolvedServlet resolvedServlet = application.resolveServlet(dispatchPath);
        if (resolvedServlet == null) {
            throw new jakarta.servlet.ServletException("No servlet for dispatcher path " + dispatchPath);
        }
        ServletRuntime runtime = resolvedServlet.runtime();

        if (dispatcherType == DispatcherType.FORWARD) {
            ensureForwardAllowed(sourceResponse);
            sourceResponse.resetBuffer();
            sourceResponse.clearErrorState();
        }

        String applicationRelative = application.contextPath.isEmpty() ? dispatchPath : dispatchPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        String servletPath = resolvedServlet.pathMatch().servletPath();
        String pathInfo = resolvedServlet.pathMatch().pathInfo();
        Runnable completionAction = null;
        if (dispatcherType == DispatcherType.FORWARD) {
            applyForwardAttributes(sourceRequest);
        } else if (dispatcherType == DispatcherType.INCLUDE) {
            IncludeAttributeSnapshot includeSnapshot = snapshotIncludeAttributes(sourceRequest);
            applyIncludeAttributes(sourceRequest, dispatchPath, application.contextPath, servletPath, pathInfo, dispatchQueryString);
            completionAction = () -> restoreIncludeAttributes(sourceRequest, includeSnapshot);
        }

        String effectiveRequestUri = dispatcherType == DispatcherType.FORWARD ? dispatchPath : sourceRequest.requestUri();
        String effectiveQueryString = dispatcherType == DispatcherType.FORWARD ? dispatchQueryString : sourceRequest.queryString();

        ServletRequestContext dispatchRequest = sourceRequest.forDispatch(
                effectiveRequestUri,
                effectiveQueryString,
                servletPath,
                pathInfo,
                dispatcherType);
        DispatchResult dispatchResult = dispatch(application,
                runtime,
                dispatchRequest,
                sourceResponse,
                new SessionHolder(application, dispatchRequest, application.servletContext, sourceRequest.sessionState()),
                sourceRequest.request().method().name(),
                null,
                request,
                response,
                completionAction,
                false);
        if (dispatcherType == DispatcherType.FORWARD && !dispatchResult.asyncStarted()) {
            sourceResponse.finish();
        }
    }

    private void dispatchNamedFromProxy(String contextPath,
                                        String targetServletName,
                                        jakarta.servlet.ServletRequest request,
                                        jakarta.servlet.ServletResponse response,
                                        DispatcherType dispatcherType) throws Exception {
        InternalRequestBridge requestBridge = DispatchBridgeSupport.requestBridge(request);
        InternalResponseBridge responseBridge = DispatchBridgeSupport.responseBridge(response);
        ServletRequestContext sourceRequest = requestBridge.requestContext();
        ServletResponseContext sourceResponse = responseBridge.responseContext();

        DeployedApplication application = resolveApplicationByContextPath(contextPath);
        if (application == null) {
            throw new jakarta.servlet.ServletException("No application for dispatcher context " + contextPath);
        }
        ServletRuntime runtime = application.resolveServletByName(targetServletName);
        if (runtime == null) {
            throw new jakarta.servlet.ServletException("No servlet named " + targetServletName + " for context " + contextPath);
        }

        if (dispatcherType == DispatcherType.FORWARD) {
            ensureForwardAllowed(sourceResponse);
            sourceResponse.resetBuffer();
            sourceResponse.clearErrorState();
        }

        ServletRequestContext dispatchRequest = sourceRequest.forDispatch(
                sourceRequest.requestUri(),
                sourceRequest.queryString(),
                sourceRequest.servletPath(),
                sourceRequest.pathInfo(),
                dispatcherType,
                null);
        DispatchResult dispatchResult = dispatch(application,
                runtime,
                dispatchRequest,
                sourceResponse,
                new SessionHolder(application, dispatchRequest, application.servletContext, sourceRequest.sessionState()),
                sourceRequest.request().method().name(),
                null,
                request,
                response,
                null,
                false);
        if (dispatcherType == DispatcherType.FORWARD && !dispatchResult.asyncStarted()) {
            sourceResponse.finish();
        }
    }

    private void asyncDispatchFromContext(String contextPath,
                                          String targetPath,
                                          ServletRequest request,
                                          ServletResponse response) throws Exception {
        InternalRequestBridge requestBridge = DispatchBridgeSupport.requestBridge(request);
        InternalResponseBridge responseBridge = DispatchBridgeSupport.responseBridge(response);
        ServletRequestContext sourceRequest = requestBridge.requestContext();
        ServletResponseContext sourceResponse = responseBridge.responseContext();

        String absolutePath = normalizeDispatchPath(contextPath, targetPath);
        String dispatchPath = stripQueryString(absolutePath);
        String dispatchQueryString = extractQueryString(absolutePath);
        DeployedApplication application = resolveApplication(dispatchPath);
        if (application == null) {
            throw new jakarta.servlet.ServletException("No application for async dispatch path " + dispatchPath);
        }
        ResolvedServlet resolvedServlet = application.resolveServlet(dispatchPath);
        if (resolvedServlet == null) {
            throw new jakarta.servlet.ServletException("No servlet for async dispatch path " + dispatchPath);
        }
        ServletRuntime runtime = resolvedServlet.runtime();

        String applicationRelative = application.contextPath.isEmpty() ? dispatchPath : dispatchPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        String servletPath = resolvedServlet.pathMatch().servletPath();
        String pathInfo = resolvedServlet.pathMatch().pathInfo();

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
                request,
                response,
                null,
                false);
    }

    private void ensureForwardAllowed(ServletResponseContext responseContext) {
        if (responseContext.isCommitted()) {
            throw new IllegalStateException("Cannot forward after response has been committed");
        }
    }

    private DispatchResult dispatch(DeployedApplication application,
                                    ServletRuntime runtime,
                                    ServletRequestContext requestContext,
                                    ServletResponseContext responseContext,
                                    SessionHolder sessionHolder,
                                    String httpMethod,
                                    ResponseSink responseSink,
                                    ServletRequest requestOverride,
                                    ServletResponse responseOverride,
                                    Runnable completionAction,
                                    boolean invokeRequestListeners) throws Exception {
        AsyncHolder asyncHolder = new AsyncHolder();
        HttpServletResponse response = ServletProxyFactory.createResponse(responseContext);

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
                            public Cookie sessionCookie() {
                                return sessionHolder.sessionCookie();
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
                                asyncExecutor,
                                asyncHolder.restoreAction());
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
        DispatchBridgeSupport.Binding<ServletRequest> requestBinding =
                DispatchBridgeSupport.bindRequest(requestOverride, request);
        DispatchBridgeSupport.Binding<ServletResponse> responseBinding =
                DispatchBridgeSupport.bindResponse(responseOverride, response);
        ServletRequest dispatchRequest = requestBinding.exposed();
        ServletResponse dispatchResponse = responseBinding.exposed();
        asyncHolder.request(dispatchRequest);
        asyncHolder.response(dispatchResponse);
        asyncHolder.restoreAction(() -> {
            requestBinding.restore().run();
            responseBinding.restore().run();
            if (completionAction != null) {
                completionAction.run();
            }
        });
        ServletRequestEvent requestEvent = ServletProxyFactory.createServletRequestEvent(application.servletContext, dispatchRequest);

        try {
            if (invokeRequestListeners) {
                for (ServletRequestListener listener : application.servletRequestListeners) {
                    listener.requestInitialized(requestEvent);
                }
            }
            List<Filter> resolvedFilters = application.resolveFilters(requestContext, runtime);
            // Prepend global filters (e.g. MCP traffic interceptor)
            GlobalFilterProvider gfp = globalFilterProvider;
            if (gfp != null) {
                List<Filter> globalFilters = gfp.createFilters(application.contextPath(), application.name());
                if (globalFilters != null && !globalFilters.isEmpty()) {
                    List<Filter> combined = new ArrayList<>(globalFilters.size() + resolvedFilters.size());
                    combined.addAll(globalFilters);
                    combined.addAll(resolvedFilters);
                    resolvedFilters = combined;
                }
            }
            new SimpleFilterChain(resolvedFilters, runtime.servlet)
                    .doFilter(dispatchRequest, dispatchResponse);
            return new DispatchResult(sessionHolder, asyncHolder.isStarted());
        } finally {
            if (invokeRequestListeners && !asyncHolder.isStarted()) {
                for (int index = application.servletRequestListeners.size() - 1; index >= 0; index--) {
                    application.servletRequestListeners.get(index).requestDestroyed(requestEvent);
                }
            }
            if (!asyncHolder.isStarted()) {
                asyncHolder.restoreAction().run();
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

    private IncludeAttributeSnapshot snapshotIncludeAttributes(ServletRequestContext requestContext) {
        return new IncludeAttributeSnapshot(
                requestContext.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI),
                requestContext.getAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH),
                requestContext.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH),
                requestContext.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO),
                requestContext.getAttribute(RequestDispatcher.INCLUDE_QUERY_STRING));
    }

    private void restoreIncludeAttributes(ServletRequestContext requestContext, IncludeAttributeSnapshot snapshot) {
        restoreAttribute(requestContext, RequestDispatcher.INCLUDE_REQUEST_URI, snapshot.requestUri());
        restoreAttribute(requestContext, RequestDispatcher.INCLUDE_CONTEXT_PATH, snapshot.contextPath());
        restoreAttribute(requestContext, RequestDispatcher.INCLUDE_SERVLET_PATH, snapshot.servletPath());
        restoreAttribute(requestContext, RequestDispatcher.INCLUDE_PATH_INFO, snapshot.pathInfo());
        restoreAttribute(requestContext, RequestDispatcher.INCLUDE_QUERY_STRING, snapshot.queryString());
    }

    private void restoreAttribute(ServletRequestContext requestContext, String name, Object value) {
        if (value == null) {
            requestContext.removeAttribute(name);
            return;
        }
        requestContext.setAttribute(name, value);
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
        ResolvedServlet resolvedErrorServlet = application.resolveServlet(dispatchPath);
        if (resolvedErrorServlet == null) {
            return null;
        }
        ServletRuntime errorRuntime = resolvedErrorServlet.runtime();

        responseContext.reset();
        responseContext.setStatus(statusCode);
        populateErrorAttributes(sourceRequest, servletName, statusCode, message, throwable);

        String applicationRelative = application.contextPath.isEmpty()
                ? dispatchPath
                : dispatchPath.substring(application.contextPath.length());
        if (applicationRelative.isEmpty()) {
            applicationRelative = "/";
        }
        String servletPath = resolvedErrorServlet.pathMatch().servletPath();
        String pathInfo = resolvedErrorServlet.pathMatch().pathInfo();

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
                    null,
                    null,
                    null,
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

    private DeployedApplication resolveApplicationByContextPath(String contextPath) {
        return applications.get(applicationKey(contextPath));
    }

    private boolean hasNamedDispatcher(String contextPath, String servletName) {
        DeployedApplication application = resolveApplicationByContextPath(contextPath);
        return application != null && application.resolveServletByName(servletName) != null;
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
            if (pair.length == 2 && sessionCookieSettings.name().equals(pair[0].trim())) {
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
            try {
                if (application.servletContext.getResource(candidate) != null) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Fall back to servlet mapping resolution when the resource lookup is unavailable.
            }
            // Check if there's a servlet mapping that can handle this welcome file
            String testPath = application.contextPath + candidate;
            ResolvedServlet matched = application.resolveServlet(testPath);
            if (matched != null) {
                return candidate;
            }
        }
        return null;
    }

    private record DeployedApplication(
            String name,
            String contextPath,
            ServletContext servletContext,
            Map<String, ServletRuntime> servlets,
            Map<String, ServletRuntime> servletsByName,
            ServletPathMapper servletPathMapper,
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

        private ResolvedServlet resolveServlet(String requestPath) {
            String applicationRelative = contextPath.isEmpty() ? requestPath : requestPath.substring(contextPath.length());
            if (applicationRelative.isEmpty()) {
                applicationRelative = "/";
            }
            ServletPathMatch pathMatch = servletPathMapper.resolve(servlets.keySet(), applicationRelative);
            if (pathMatch == null) {
                return null;
            }
            ServletRuntime runtime = servlets.get(pathMatch.mapping());
            if (runtime == null) {
                return null;
            }
            return new ResolvedServlet(runtime, pathMatch);
        }

        private ServletRuntime resolveServletByName(String servletName) {
            return servletsByName.get(servletName);
        }

        private List<Filter> resolveFilters(ServletRequestContext requestContext, ServletRuntime runtime) {
            String effectivePath = requestContext.dispatchTargetPath();
            List<Filter> resolvedFilters = new ArrayList<>();
            if (effectivePath != null) {
                for (FilterRuntime filterRuntime : filters) {
                    if (filterRuntime.registration.isUrlPatternMapping()
                            && filterRuntime.registration.matchesPath(effectivePath, requestContext.dispatcherType())) {
                        resolvedFilters.add(filterRuntime.filter);
                    }
                }
            }
            for (FilterRuntime filterRuntime : filters) {
                if (filterRuntime.registration.isServletNameMapping()
                        && filterRuntime.registration.matchesServlet(runtime.servletName, requestContext.dispatcherType())) {
                    resolvedFilters.add(filterRuntime.filter);
                }
            }
            return resolvedFilters;
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
        private final String servletName;
        private final Servlet servlet;

        private ServletRuntime(String mapping, String servletName, Servlet servlet) {
            this.mapping = mapping;
            this.servletName = servletName;
            this.servlet = servlet;
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

    private record ResolvedServlet(ServletRuntime runtime, ServletPathMatch pathMatch) {
    }

    private record IncludeAttributeSnapshot(Object requestUri,
                                            Object contextPath,
                                            Object servletPath,
                                            Object pathInfo,
                                            Object queryString) {
    }

    private static class AsyncHolder {
        private volatile VeloAsyncContext context;
        private volatile ServletRequest request;
        private volatile ServletResponse response;
        private volatile Runnable restoreAction = () -> {
        };

        void start(VeloAsyncContext ctx) {
            this.context = ctx;
        }

        boolean isStarted() {
            return context != null;
        }

        VeloAsyncContext context() {
            return context;
        }

        void request(ServletRequest request) {
            this.request = request;
        }

        ServletRequest request() {
            return request;
        }

        void response(ServletResponse response) {
            this.response = response;
        }

        ServletResponse response() {
            return response;
        }

        void restoreAction(Runnable restoreAction) {
            this.restoreAction = restoreAction == null ? () -> {
            } : restoreAction;
        }

        Runnable restoreAction() {
            return restoreAction;
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

        private Cookie sessionCookie() {
            if (!shouldSetCookie() || session == null) {
                return null;
            }
            return sessionCookieSettings.createSessionCookie(
                    session.getId(),
                    application.contextPath(),
                    requestContext.requestSecure());
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
