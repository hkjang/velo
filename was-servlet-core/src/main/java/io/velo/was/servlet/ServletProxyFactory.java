package io.velo.was.servlet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ServletProxyFactory {

    private ServletProxyFactory() {
    }

    static ServletContext createServletContext(String contextPath,
                                               String applicationName,
                                               ClassLoader classLoader,
                                               Map<String, String> initParameters,
                                               Map<String, Object> serverAttributes,
                                               RequestDispatcherResolver dispatcherResolver) {
        Map<String, Object> attributes = new LinkedHashMap<>();

        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getContextPath" -> contextPath;
            case "getMajorVersion", "getEffectiveMajorVersion" -> 6;
            case "getMinorVersion", "getEffectiveMinorVersion" -> 1;
            case "getServerInfo" -> "velo-was/0.1";
            case "getServletContextName", "getVirtualServerName" -> applicationName;
            case "getClassLoader" -> classLoader;
            case "getAttribute" -> {
                Object value = attributes.get((String) args[0]);
                yield value != null ? value : serverAttributes.get((String) args[0]);
            }
            case "setAttribute" -> {
                if (args[1] == null) {
                    attributes.remove((String) args[0]);
                } else {
                    attributes.put((String) args[0], args[1]);
                }
                yield null;
            }
            case "removeAttribute" -> {
                attributes.remove((String) args[0]);
                yield null;
            }
            case "getAttributeNames" -> Collections.enumeration(attributes.keySet());
            case "getInitParameter" -> initParameters.get((String) args[0]);
            case "getInitParameterNames" -> Collections.enumeration(initParameters.keySet());
            case "setInitParameter" -> false;
            case "log" -> {
                if (args != null && args.length > 0) {
                    System.out.println("[servlet-context] " + args[0]);
                }
                yield null;
            }
            case "getRequestCharacterEncoding", "getResponseCharacterEncoding" -> "UTF-8";
            case "setRequestCharacterEncoding", "setResponseCharacterEncoding" -> null;
            case "addListener" -> null;
            case "createListener" -> ((Class<?>) args[0]).getDeclaredConstructor().newInstance();
            case "getSessionTimeout" -> 30;
            case "setSessionTimeout" -> null;
            case "getResource", "getResourceAsStream", "getContext", "getNamedDispatcher",
                 "addServlet", "createServlet", "getServletRegistration",
                 "getServletRegistrations", "addFilter", "createFilter", "getFilterRegistration",
                 "getFilterRegistrations", "getJspConfigDescriptor", "getServlet", "getServlets",
                 "getServletNames", "declareRoles", "getRealPath", "getMimeType",
                 "getDefaultSessionTrackingModes", "getEffectiveSessionTrackingModes",
                 "getSessionCookieConfig" -> null;
            case "getRequestDispatcher" -> createRequestDispatcher(dispatcherResolver, (String) args[0]);
            case "setSessionTrackingModes" -> null;
            case "getResourcePaths" -> Set.of();
            default -> defaultValue(proxy, method, args);
        };

        return (ServletContext) Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{ServletContext.class},
                handler);
    }

    static ServletConfig createServletConfig(String servletName,
                                             ServletContext servletContext,
                                             Map<String, String> initParameters) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getServletName" -> servletName;
            case "getServletContext" -> servletContext;
            case "getInitParameter" -> initParameters.get((String) args[0]);
            case "getInitParameterNames" -> Collections.enumeration(initParameters.keySet());
            default -> defaultValue(proxy, method, args);
        };

        return (ServletConfig) Proxy.newProxyInstance(
                servletContext.getClassLoader(),
                new Class<?>[]{ServletConfig.class},
                handler);
    }

    static FilterConfig createFilterConfig(String filterName,
                                           ServletContext servletContext,
                                           Map<String, String> initParameters) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getFilterName" -> filterName;
            case "getServletContext" -> servletContext;
            case "getInitParameter" -> initParameters.get((String) args[0]);
            case "getInitParameterNames" -> Collections.enumeration(initParameters.keySet());
            default -> defaultValue(proxy, method, args);
        };

        return (FilterConfig) Proxy.newProxyInstance(
                servletContext.getClassLoader(),
                new Class<?>[]{FilterConfig.class},
                handler);
    }

    static ServletRequestEvent createServletRequestEvent(ServletContext servletContext, HttpServletRequest request) {
        return new ServletRequestEvent(servletContext, request);
    }

    static HttpServletRequest createRequest(ServletRequestContext requestContext,
                                           SessionAccessor sessionAccessor,
                                           RequestDispatcherResolver dispatcherResolver,
                                           AsyncContextAccessor asyncContextAccessor) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getMethod" -> requestContext.request().method().name();
            case "getRequestURI" -> requestContext.requestUri();
            case "getRequestURL" -> new StringBuffer(buildRequestUrl(requestContext));
            case "getProtocol" -> requestContext.request().protocolVersion().text();
            case "getScheme" -> requestContext.exchange().secure() ? "https" : "http";
            case "getServerName" -> requestContext.localAddress().getHostString();
            case "getServerPort" -> requestContext.localAddress().getPort();
            case "getRemoteAddr" -> requestContext.remoteAddress().getAddress().getHostAddress();
            case "getRemoteHost" -> requestContext.remoteAddress().getHostString();
            case "getRemotePort" -> requestContext.remoteAddress().getPort();
            case "getLocalAddr" -> requestContext.localAddress().getAddress().getHostAddress();
            case "getLocalName" -> requestContext.localAddress().getHostString();
            case "getLocalPort" -> requestContext.localAddress().getPort();
            case "isSecure" -> requestContext.exchange().secure();
            case "getContentType" -> requestContext.header("Content-Type");
            case "getContentLength" -> requestContext.body().length;
            case "getContentLengthLong" -> (long) requestContext.body().length;
            case "getCharacterEncoding" -> requestContext.characterEncoding();
            case "setCharacterEncoding" -> {
                requestContext.setCharacterEncoding((String) args[0]);
                yield null;
            }
            case "getInputStream" -> new ServletBodyInputStream(requestContext.body());
            case "getReader" -> new BufferedReader(new InputStreamReader(new ServletBodyInputStream(requestContext.body()), requestContext.charset()));
            case "getHeader" -> requestContext.header((String) args[0]);
            case "getHeaders" -> requestContext.headers((String) args[0]);
            case "getHeaderNames" -> requestContext.headerNames();
            case "getIntHeader" -> {
                String value = requestContext.header((String) args[0]);
                yield value == null ? -1 : Integer.parseInt(value);
            }
            case "getDateHeader" -> -1L;
            case "getCookies" -> requestContext.cookies();
            case "getQueryString" -> requestContext.queryString();
            case "getContextPath" -> requestContext.contextPath();
            case "getServletPath" -> requestContext.servletPath();
            case "getPathInfo" -> requestContext.pathInfo();
            case "getPathTranslated" -> null;
            case "getParameter" -> requestContext.parameter((String) args[0]);
            case "getParameterMap" -> requestContext.parameterMap();
            case "getParameterNames" -> requestContext.parameterNames();
            case "getParameterValues" -> requestContext.parameterValues((String) args[0]);
            case "getAttribute" -> requestContext.getAttribute((String) args[0]);
            case "setAttribute" -> {
                requestContext.setAttribute((String) args[0], args[1]);
                yield null;
            }
            case "removeAttribute" -> {
                requestContext.removeAttribute((String) args[0]);
                yield null;
            }
            case "getAttributeNames" -> requestContext.attributeNames();
            case "getLocale" -> requestContext.locale();
            case "getLocales" -> Collections.enumeration(java.util.List.of(requestContext.locale()));
            case "getDispatcherType" -> requestContext.dispatcherType();
            case "getServletContext" -> requestContext.servletContext();
            case "getRequestId", "getProtocolRequestId" -> requestContext.exchange().path();
            case "getRequestDispatcher" -> createRequestDispatcher(
                    dispatcherResolver,
                    resolveDispatcherPath(requestContext, (String) args[0]));
            case "requestContext" -> requestContext;
            case "getSession" -> {
                boolean create = args == null || args.length == 0 || Boolean.TRUE.equals(args[0]);
                yield sessionAccessor.session(create);
            }
            case "changeSessionId" -> {
                HttpSession session = sessionAccessor.session(false);
                yield session == null ? null : session.getId();
            }
            case "isRequestedSessionIdValid" -> sessionAccessor.session(false) != null;
            case "isRequestedSessionIdFromCookie" -> sessionAccessor.session(false) != null;
            case "isRequestedSessionIdFromURL", "isRequestedSessionIdFromUrl" -> false;
            case "getRequestedSessionId" -> {
                HttpSession session = sessionAccessor.session(false);
                yield session == null ? null : session.getId();
            }
            case "getParts" -> requestContext.getParts();
            case "getPart" -> requestContext.getPart((String) args[0]);
            case "getAuthType", "getRemoteUser", "getUserPrincipal" -> null;
            case "isUserInRole", "authenticate" -> false;
            case "login", "logout" -> null;
            case "upgrade" -> {
                throw new ServletException("Protocol upgrade is not implemented yet");
            }
            case "startAsync" -> {
                if (args == null || args.length == 0) {
                    yield asyncContextAccessor.startAsync((HttpServletRequest) proxy, null);
                } else {
                    yield asyncContextAccessor.startAsync((ServletRequest) args[0], (ServletResponse) args[1]);
                }
            }
            case "isAsyncStarted" -> asyncContextAccessor.isAsyncStarted();
            case "isAsyncSupported" -> asyncContextAccessor.isAsyncSupported();
            case "getAsyncContext" -> {
                AsyncContext ctx = asyncContextAccessor.getAsyncContext();
                if (ctx == null) {
                    throw new IllegalStateException("Async not started. Call startAsync() first.");
                }
                yield ctx;
            }
            default -> defaultValue(proxy, method, args);
        };

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class, InternalRequestBridge.class},
                handler);
    }

    static HttpServletResponse createResponse(ServletResponseContext responseContext) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setStatus" -> {
                responseContext.setStatus((Integer) args[0]);
                yield null;
            }
            case "getStatus" -> responseContext.status();
            case "sendError" -> {
                responseContext.setStatus((Integer) args[0]);
                if (args.length > 1 && args[1] instanceof String message) {
                    responseContext.writer().write(message);
                }
                yield null;
            }
            case "sendRedirect" -> {
                responseContext.setStatus(302);
                responseContext.setHeader("Location", (String) args[0]);
                yield null;
            }
            case "setHeader" -> {
                responseContext.setHeader((String) args[0], (String) args[1]);
                yield null;
            }
            case "addHeader" -> {
                responseContext.addHeader((String) args[0], (String) args[1]);
                yield null;
            }
            case "containsHeader" -> responseContext.containsHeader((String) args[0]);
            case "addCookie" -> {
                responseContext.addCookie((jakarta.servlet.http.Cookie) args[0]);
                yield null;
            }
            case "getCharacterEncoding" -> responseContext.characterEncoding();
            case "setCharacterEncoding" -> {
                responseContext.setCharacterEncoding((String) args[0]);
                yield null;
            }
            case "getContentType" -> responseContext.contentType();
            case "setContentType" -> {
                responseContext.setContentType((String) args[0]);
                yield null;
            }
            case "getWriter" -> responseContext.writer();
            case "getOutputStream" -> responseContext.outputStream();
            case "isCommitted" -> responseContext.isCommitted();
            case "responseContext" -> responseContext;
            case "reset", "resetBuffer" -> {
                responseContext.reset();
                yield null;
            }
            case "flushBuffer" -> {
                responseContext.writer().flush();
                yield null;
            }
            case "setBufferSize", "setLocale", "addDateHeader", "setDateHeader", "addIntHeader", "setIntHeader" -> null;
            case "getBufferSize" -> 0;
            case "getLocale" -> java.util.Locale.getDefault();
            case "setContentLength", "setContentLengthLong" -> null;
            case "encodeURL", "encodeRedirectURL", "encodeUrl", "encodeRedirectUrl" -> args[0];
            default -> defaultValue(proxy, method, args);
        };

        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class, InternalResponseBridge.class},
                handler);
    }

    static HttpSession createSessionProxy(SessionState sessionState,
                                          InMemoryHttpSessionStore sessionStore,
                                          ServletContext servletContext,
                                          boolean isNew) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getId" -> sessionState.getId();
            case "getCreationTime" -> sessionState.getCreationTime();
            case "getLastAccessedTime" -> sessionState.getLastAccessedTime();
            case "getServletContext" -> servletContext;
            case "setMaxInactiveInterval" -> {
                sessionState.setMaxInactiveIntervalSeconds((Integer) args[0]);
                yield null;
            }
            case "getMaxInactiveInterval" -> sessionState.getMaxInactiveIntervalSeconds();
            case "getAttribute", "getValue" -> sessionState.attributes().get((String) args[0]);
            case "getAttributeNames" -> Collections.enumeration(sessionState.attributes().keySet());
            case "getValueNames" -> sessionState.attributes().keySet().toArray(String[]::new);
            case "setAttribute", "putValue" -> {
                sessionState.attributes().put((String) args[0], args[1]);
                yield null;
            }
            case "removeAttribute", "removeValue" -> {
                sessionState.attributes().remove((String) args[0]);
                yield null;
            }
            case "invalidate" -> {
                sessionState.invalidate();
                sessionStore.invalidate(sessionState.getId());
                yield null;
            }
            case "isNew" -> isNew;
            default -> defaultValue(proxy, method, args);
        };

        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                handler);
    }

    private static String buildRequestUrl(ServletRequestContext requestContext) {
        return "http://" + requestContext.localAddress().getHostString() + ":" + requestContext.localAddress().getPort()
                + requestContext.exchange().path();
    }

    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> {
                Class<?> type = method.getReturnType();
                if (type == Void.TYPE) {
                    yield null;
                }
                if (type == Boolean.TYPE) {
                    yield false;
                }
                if (type == Integer.TYPE) {
                    yield 0;
                }
                if (type == Long.TYPE) {
                    yield 0L;
                }
                if (type == Double.TYPE) {
                    yield 0D;
                }
                if (type == Float.TYPE) {
                    yield 0F;
                }
                if (type == Short.TYPE) {
                    yield (short) 0;
                }
                if (type == Byte.TYPE) {
                    yield (byte) 0;
                }
                if (type == Character.TYPE) {
                    yield (char) 0;
                }
                yield null;
            }
        };
    }

    interface SessionAccessor {
        HttpSession session(boolean create);
    }

    interface RequestDispatcherResolver {
        void forward(String path, ServletRequest request, ServletResponse response) throws Exception;
        void include(String path, ServletRequest request, ServletResponse response) throws Exception;
    }

    interface AsyncContextAccessor {
        AsyncContext startAsync(ServletRequest request, ServletResponse response);
        boolean isAsyncStarted();
        boolean isAsyncSupported();
        AsyncContext getAsyncContext();
    }

    private static RequestDispatcher createRequestDispatcher(RequestDispatcherResolver resolver, String path) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "forward" -> {
                resolver.forward(path, (ServletRequest) args[0], (ServletResponse) args[1]);
                yield null;
            }
            case "include" -> {
                resolver.include(path, (ServletRequest) args[0], (ServletResponse) args[1]);
                yield null;
            }
            default -> defaultValue(proxy, method, args);
        };

        return (RequestDispatcher) Proxy.newProxyInstance(
                RequestDispatcher.class.getClassLoader(),
                new Class<?>[]{RequestDispatcher.class},
                handler);
    }

    private static String resolveDispatcherPath(ServletRequestContext requestContext, String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.startsWith("/")) {
            return path;
        }

        String requestPath = requestContext.requestPathWithinApplication();
        int lastSlash = requestPath.lastIndexOf('/');
        String baseDirectory = lastSlash >= 0 ? requestPath.substring(0, lastSlash + 1) : "/";
        return normalizeRelativePath(baseDirectory + path);
    }

    private static String normalizeRelativePath(String path) {
        java.util.Deque<String> segments = new java.util.ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.addLast(part);
        }
        return "/" + String.join("/", segments);
    }
}
