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
        this.parameters = parseParameters(exchange.request().uri(), charset());
        if (this.sessionState != null) {
            this.sessionState.touch();
        }
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
}
