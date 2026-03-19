package io.velo.was.servlet;

import io.netty.handler.codec.http.FullHttpRequest;
import io.velo.was.http.HttpExchange;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.Part;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class ServletRequestContext {

    private final HttpExchange exchange;
    private final ServletContext servletContext;
    private final String contextPath;
    private final String requestUri;
    private final String queryString;
    private String servletPath;
    private String pathInfo;
    private DispatcherType dispatcherType;
    private final Map<String, Object> attributes;
    private final Map<String, List<String>> parameters;
    private String characterEncoding = StandardCharsets.UTF_8.name();
    private SessionState sessionState;
    private boolean sessionCreated;
    private volatile ExternalRequestTarget externalRequestTarget;

    ServletRequestContext(HttpExchange exchange,
                          ServletContext servletContext,
                          String contextPath,
                          String servletPath,
                          String pathInfo,
                          SessionState sessionState) {
        this(
                exchange,
                servletContext,
                contextPath,
                exchange.path(),
                extractQueryString(exchange.request().uri()),
                servletPath,
                pathInfo,
                DispatcherType.REQUEST,
                sessionState,
                new LinkedHashMap<>());
    }

    ServletRequestContext(HttpExchange exchange,
                          ServletContext servletContext,
                          String contextPath,
                          String requestUri,
                          String queryString,
                          String servletPath,
                          String pathInfo,
                          DispatcherType dispatcherType,
                          SessionState sessionState,
                          Map<String, Object> attributes) {
        this.exchange = exchange;
        this.servletContext = servletContext;
        this.contextPath = contextPath;
        this.requestUri = requestUri;
        this.queryString = queryString;
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.dispatcherType = dispatcherType;
        this.sessionState = sessionState;
        this.attributes = attributes;
        this.parameters = parseAllParameters(exchange, charset());
    }

    public HttpExchange exchange() {
        return exchange;
    }

    public FullHttpRequest request() {
        return exchange.request();
    }

    public ServletContext servletContext() {
        return servletContext;
    }

    public String contextPath() {
        return contextPath;
    }

    public String requestUri() {
        return requestUri;
    }

    public String queryString() {
        return queryString;
    }

    public String requestPathWithinApplication() {
        String value = servletPath == null ? "" : servletPath;
        if (pathInfo != null) {
            value += pathInfo;
        }
        return value.isBlank() ? "/" : value;
    }

    public String servletPath() {
        return servletPath;
    }

    public String pathInfo() {
        return pathInfo;
    }

    public DispatcherType dispatcherType() {
        return dispatcherType;
    }

    public ServletRequestContext forDispatch(String requestUri,
                                             String queryString,
                                             String servletPath,
                                             String pathInfo,
                                             DispatcherType dispatcherType) {
        return new ServletRequestContext(
                exchange,
                servletContext,
                contextPath,
                requestUri,
                queryString,
                servletPath,
                pathInfo,
                dispatcherType,
                sessionState,
                attributes);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            attributes.remove(name);
            return;
        }
        attributes.put(name, value);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public Enumeration<String> attributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    public String characterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        if (characterEncoding != null && !characterEncoding.isBlank()) {
            this.characterEncoding = characterEncoding;
        }
    }

    public Charset charset() {
        return Charset.forName(characterEncoding);
    }

    public byte[] body() {
        byte[] body = new byte[request().content().readableBytes()];
        request().content().getBytes(request().content().readerIndex(), body);
        return body;
    }

    public String parameter(String name) {
        List<String> values = parameters.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public Map<String, String[]> parameterMap() {
        Map<String, String[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toArray(String[]::new));
        }
        return result;
    }

    public Enumeration<String> parameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    public String[] parameterValues(String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : values.toArray(String[]::new);
    }

    public Enumeration<String> headerNames() {
        return Collections.enumeration(request().headers().names());
    }

    public Enumeration<String> headers(String name) {
        return Collections.enumeration(request().headers().getAll(name));
    }

    public String header(String name) {
        return request().headers().get(name);
    }

    public Cookie[] cookies() {
        String cookieHeader = header("Cookie");
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return new Cookie[0];
        }
        String[] segments = cookieHeader.split(";");
        List<Cookie> cookies = new ArrayList<>(segments.length);
        for (String segment : segments) {
            String[] pair = segment.trim().split("=", 2);
            if (pair.length == 2) {
                cookies.add(new Cookie(pair[0].trim(), pair[1].trim()));
            }
        }
        return cookies.toArray(Cookie[]::new);
    }

    public SessionState sessionState() {
        return sessionState;
    }

    public void sessionState(SessionState sessionState, boolean created) {
        this.sessionState = sessionState;
        this.sessionCreated = created;
    }

    public boolean sessionCreated() {
        return sessionCreated;
    }

    public Locale locale() {
        return Locale.getDefault();
    }

    private volatile List<VeloPart> parsedParts;

    public Collection<Part> getParts() {
        if (parsedParts == null) {
            parsedParts = MultipartParser.parse(body(), header("Content-Type"));
        }
        return Collections.unmodifiableList(parsedParts);
    }

    public Part getPart(String name) {
        return getParts().stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) exchange.remoteAddress();
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) exchange.localAddress();
    }

    public String requestScheme() {
        return externalRequestTarget().scheme();
    }

    public boolean requestSecure() {
        return "https".equalsIgnoreCase(requestScheme());
    }

    public String requestServerName() {
        return externalRequestTarget().host();
    }

    public int requestServerPort() {
        return externalRequestTarget().port();
    }

    private static Map<String, List<String>> parseAllParameters(HttpExchange exchange, Charset charset) {
        Map<String, List<String>> params = new LinkedHashMap<>();

        // 1. Parse query string parameters from URI
        mergeQueryString(params, exchange.request().uri(), charset);

        // 2. Parse POST body parameters (application/x-www-form-urlencoded)
        FullHttpRequest request = exchange.request();
        String method = request.method().name();
        if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))
                && request.content().readableBytes() > 0) {
            String contentType = request.headers().get("Content-Type");
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/x-www-form-urlencoded")) {
                String body = request.content().toString(charset);
                for (String part : body.split("&")) {
                    if (part.isBlank()) continue;
                    String[] pair = part.split("=", 2);
                    String key = java.net.URLDecoder.decode(pair[0], charset);
                    String value = pair.length > 1 ? java.net.URLDecoder.decode(pair[1], charset) : "";
                    params.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
                }
            }
        }

        return params;
    }

    private static void mergeQueryString(Map<String, List<String>> params, String uri, Charset charset) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0 || queryStart == uri.length() - 1) return;
        String query = uri.substring(queryStart + 1);
        for (String part : query.split("&")) {
            if (part.isBlank()) continue;
            String[] pair = part.split("=", 2);
            String key = java.net.URLDecoder.decode(pair[0], charset);
            String value = pair.length > 1 ? java.net.URLDecoder.decode(pair[1], charset) : "";
            params.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
    }

    private static Map<String, List<String>> parseParameters(String uri, Charset charset) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0 || queryStart == uri.length() - 1) {
            return Map.of();
        }

        String query = uri.substring(queryStart + 1);
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pair = part.split("=", 2);
            String key = java.net.URLDecoder.decode(pair[0], charset);
            String value = pair.length > 1 ? java.net.URLDecoder.decode(pair[1], charset) : "";
            parameters.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return parameters;
    }

    private static String extractQueryString(String uri) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0 || queryStart == uri.length() - 1) {
            return null;
        }
        return uri.substring(queryStart + 1);
    }

    private ExternalRequestTarget externalRequestTarget() {
        ExternalRequestTarget target = externalRequestTarget;
        if (target == null) {
            target = resolveExternalRequestTarget();
            externalRequestTarget = target;
        }
        return target;
    }

    /**
     * Resolves the externally visible scheme/host/port so apps behind reverse
     * proxies see the same request URL that browsers used.
     */
    private ExternalRequestTarget resolveExternalRequestTarget() {
        String forwardedHeader = header("Forwarded");
        String scheme = forwardedValue(forwardedHeader, "proto");
        if (scheme == null) {
            scheme = firstHeaderValue(header("X-Forwarded-Proto"));
        }
        if (scheme == null || scheme.isBlank()) {
            scheme = exchange.secure() ? "https" : "http";
        }
        scheme = scheme.toLowerCase(Locale.ROOT);

        HostPort hostPort = null;
        String forwardedHost = forwardedValue(forwardedHeader, "host");
        if (forwardedHost != null) {
            hostPort = parseHostPort(forwardedHost);
        }
        if (hostPort == null) {
            hostPort = parseHostPort(firstHeaderValue(header("X-Forwarded-Host")));
        }
        if (hostPort == null) {
            hostPort = parseHostPort(firstHeaderValue(header("Host")));
        }
        if (hostPort == null) {
            InetSocketAddress local = localAddress();
            if (local != null) {
                hostPort = new HostPort(local.getHostString(), local.getPort());
            } else {
                hostPort = new HostPort("localhost", defaultPort(scheme));
            }
        }

        Integer forwardedPort = parsePort(firstHeaderValue(header("X-Forwarded-Port")));
        int port = forwardedPort != null ? forwardedPort : hostPort.port();
        if (port <= 0) {
            port = defaultPort(scheme);
        }

        return new ExternalRequestTarget(scheme, hostPort.host(), port);
    }

    private static String firstHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        int commaIndex = headerValue.indexOf(',');
        String first = commaIndex >= 0 ? headerValue.substring(0, commaIndex) : headerValue;
        return unquote(first.trim());
    }

    private static String forwardedValue(String forwardedHeader, String key) {
        String entry = firstHeaderValue(forwardedHeader);
        if (entry == null) {
            return null;
        }
        for (String segment : entry.split(";")) {
            String[] pair = segment.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if (key.equalsIgnoreCase(pair[0].trim())) {
                String value = unquote(pair[1].trim());
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static HostPort parseHostPort(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = unquote(rawValue.trim());
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket > 0) {
                String host = value.substring(1, closingBracket);
                Integer port = null;
                if (closingBracket + 1 < value.length() && value.charAt(closingBracket + 1) == ':') {
                    port = parsePort(value.substring(closingBracket + 2));
                }
                return new HostPort(host, port != null ? port : -1);
            }
        }

        int lastColon = value.lastIndexOf(':');
        if (lastColon > 0 && value.indexOf(':') == lastColon) {
            Integer port = parsePort(value.substring(lastColon + 1));
            if (port != null) {
                return new HostPort(value.substring(0, lastColon), port);
            }
        }

        return new HostPort(value, -1);
    }

    private static Integer parsePort(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private record ExternalRequestTarget(String scheme, String host, int port) {
    }

    private record HostPort(String host, int port) {
    }
}
