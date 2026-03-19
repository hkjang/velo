package io.velo.was.servlet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Default servlet for serving static assets out of an exploded WAR.
 * <p>
 * The servlet focuses on browser-facing correctness for modern JS apps:
 * MIME resolution, cache headers, conditional GET, precompressed assets and
 * SPA fallback routing.
 */
public class StaticResourceServlet extends HttpServlet {

    public static final String CACHE_MAX_AGE_PARAM = "io.velo.was.static.cache.maxAgeSeconds";
    public static final String IMMUTABLE_CACHE_MAX_AGE_PARAM = "io.velo.was.static.cache.immutableMaxAgeSeconds";
    public static final String HTML_CACHE_MAX_AGE_PARAM = "io.velo.was.static.cache.htmlMaxAgeSeconds";
    public static final String PRECOMPRESSED_ENABLED_PARAM = "io.velo.was.static.precompressed.enabled";
    public static final String SPA_FALLBACK_PARAM = "io.velo.was.static.spaFallback";
    public static final String SOURCE_MAPS_ENABLED_PARAM = "io.velo.was.static.sourceMaps.enabled";

    private static final Logger log = LoggerFactory.getLogger(StaticResourceServlet.class);
    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final Pattern FINGERPRINT_PATTERN =
            Pattern.compile("(?i).*[._-][0-9a-f]{8,}(?:\\.[^.]+)+$");
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".avif", "image/avif"),
            Map.entry(".css", "text/css; charset=UTF-8"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".html", "text/html; charset=UTF-8"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".js", "text/javascript; charset=UTF-8"),
            Map.entry(".json", "application/json; charset=UTF-8"),
            Map.entry(".map", "application/json; charset=UTF-8"),
            Map.entry(".mjs", "text/javascript; charset=UTF-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".txt", "text/plain; charset=UTF-8"),
            Map.entry(".wasm", "application/wasm"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".xml", "application/xml; charset=UTF-8")
    );

    private Path webRoot;
    private int assetCacheMaxAgeSeconds;
    private int immutableCacheMaxAgeSeconds;
    private int htmlCacheMaxAgeSeconds;
    private boolean precompressedEnabled;
    private boolean sourceMapsEnabled;
    private String spaFallbackPath;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.webRoot = resolveWebRoot(config.getServletContext());
        this.assetCacheMaxAgeSeconds = parseIntConfig(CACHE_MAX_AGE_PARAM, 3600);
        this.immutableCacheMaxAgeSeconds = parseIntConfig(IMMUTABLE_CACHE_MAX_AGE_PARAM, 31536000);
        this.htmlCacheMaxAgeSeconds = parseIntConfig(HTML_CACHE_MAX_AGE_PARAM, 0);
        this.precompressedEnabled = parseBooleanConfig(PRECOMPRESSED_ENABLED_PARAM, true);
        this.sourceMapsEnabled = parseBooleanConfig(SOURCE_MAPS_ENABLED_PARAM, true);
        this.spaFallbackPath = normalizeConfiguredPath(readConfig(SPA_FALLBACK_PARAM));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        serve(req, resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        serve(req, resp);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestPath = normalizeRequestPath(request);
        if (requestPath == null) {
            log.warn("Rejected malformed static resource path: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (isRestrictedPath(requestPath)) {
            log.warn("Blocked static resource access to protected path: {}", requestPath);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (!sourceMapsEnabled && requestPath.endsWith(".map")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String rangeHeader = request.getHeader("Range");
        ResolvedStaticResource resource = resolveResource(requestPath, request.getHeader("Accept-Encoding"),
                rangeHeader == null || rangeHeader.isBlank());
        boolean spaFallback = false;
        if (resource == null && shouldServeSpaFallback(request, requestPath)) {
            resource = resolveResource(spaFallbackPath, request.getHeader("Accept-Encoding"),
                    rangeHeader == null || rangeHeader.isBlank());
            spaFallback = resource != null;
        }

        if (resource == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        ResourceMetadata metadata = metadataFor(resource);
        String etag = buildEtag(metadata);

        applyStandardHeaders(response, resource, metadata, etag, spaFallback);

        if (isNotModified(request, metadata, etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        byte[] payload = Files.readAllBytes(resource.path());
        if (rangeHeader != null && !rangeHeader.isBlank() && resource.contentEncoding() == null) {
            ByteRange range = parseRange(rangeHeader, payload.length);
            if (range == null) {
                response.setHeader("Content-Range", "bytes */" + payload.length);
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range",
                    "bytes " + range.start() + "-" + range.end() + "/" + payload.length);
            response.getOutputStream().write(payload, (int) range.start(), range.length());
            return;
        }

        response.getOutputStream().write(payload);
    }

    private void applyStandardHeaders(HttpServletResponse response,
                                      ResolvedStaticResource resource,
                                      ResourceMetadata metadata,
                                      String etag,
                                      boolean spaFallback) {
        response.setContentType(resolveContentType(resource.logicalPath()));
        response.setHeader("Cache-Control", cacheControl(resource.logicalPath(), spaFallback));
        response.setHeader("ETag", etag);
        response.setHeader("Last-Modified", formatHttpDate(metadata.lastModifiedMillis()));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Accept-Ranges", "bytes");
        if (resource.contentEncoding() != null) {
            response.setHeader("Content-Encoding", resource.contentEncoding());
            response.setHeader("Vary", "Accept-Encoding");
        }
    }

    private boolean isNotModified(HttpServletRequest request, ResourceMetadata metadata, String etag) {
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            for (String candidate : ifNoneMatch.split(",")) {
                String trimmed = candidate.trim();
                if ("*".equals(trimmed) || etag.equals(trimmed)) {
                    return true;
                }
            }
        }

        String ifModifiedSince = request.getHeader("If-Modified-Since");
        if (ifModifiedSince == null || ifModifiedSince.isBlank()) {
            return false;
        }
        try {
            long clientTime = Instant.from(HTTP_DATE_FORMATTER.parse(ifModifiedSince)).toEpochMilli();
            long resourceTime = metadata.lastModifiedMillis() - (metadata.lastModifiedMillis() % 1000L);
            return clientTime >= resourceTime;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private String cacheControl(String logicalPath, boolean spaFallback) {
        if (spaFallback || isHtml(logicalPath)) {
            return htmlCacheMaxAgeSeconds <= 0
                    ? "no-cache"
                    : "public, max-age=" + htmlCacheMaxAgeSeconds;
        }
        if (isFingerprintedAsset(logicalPath)) {
            return "public, max-age=" + immutableCacheMaxAgeSeconds + ", immutable";
        }
        return "public, max-age=" + assetCacheMaxAgeSeconds;
    }

    private boolean shouldServeSpaFallback(HttpServletRequest request, String requestPath) {
        if (spaFallbackPath == null) {
            return false;
        }
        if ("/".equals(requestPath) || spaFallbackPath.equals(requestPath)) {
            return false;
        }
        if (looksLikeAsset(requestPath)) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept == null
                || accept.contains("text/html")
                || accept.contains("application/xhtml+xml")
                || accept.contains("*/*");
    }

    private ResolvedStaticResource resolveResource(String requestPath,
                                                   String acceptEncoding,
                                                   boolean allowPrecompressed) {
        if (requestPath == null) {
            return null;
        }

        Path candidate = resolvePath(requestPath);
        if (candidate == null) {
            return null;
        }

        if (allowPrecompressed && precompressedEnabled && supportsPrecompression(requestPath)) {
            if (acceptsEncoding(acceptEncoding, "br")) {
                Path brotli = resolvePath(requestPath + ".br");
                if (brotli != null) {
                    return new ResolvedStaticResource(brotli, requestPath, "br");
                }
            }
            if (acceptsEncoding(acceptEncoding, "gzip")) {
                Path gzip = resolvePath(requestPath + ".gz");
                if (gzip != null) {
                    return new ResolvedStaticResource(gzip, requestPath, "gzip");
                }
            }
        }

        return new ResolvedStaticResource(candidate, requestPath, null);
    }

    private ResourceMetadata metadataFor(ResolvedStaticResource resource) throws IOException {
        return new ResourceMetadata(
                Files.size(resource.path()),
                Files.getLastModifiedTime(resource.path()).toMillis());
    }

    private Path resolvePath(String requestPath) {
        if (requestPath == null || webRoot == null) {
            return null;
        }
        String relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        Path resolved = webRoot.resolve(relative).normalize();
        if (!resolved.startsWith(webRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    private static Path resolveWebRoot(ServletContext servletContext) {
        String realPath = servletContext.getRealPath("/");
        if (realPath == null || realPath.isBlank()) {
            return null;
        }
        return Path.of(realPath).toAbsolutePath().normalize();
    }

    private String normalizeRequestPath(HttpServletRequest request) {
        String rawPath = request.getServletPath();
        if (rawPath != null && request.getPathInfo() != null) {
            rawPath = rawPath + request.getPathInfo();
        }
        if (rawPath == null || rawPath.isBlank()) {
            rawPath = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (rawPath == null) {
                return null;
            }
            if (contextPath != null && !contextPath.isEmpty() && rawPath.startsWith(contextPath)) {
                rawPath = rawPath.substring(contextPath.length());
            }
        }
        return normalizeConfiguredPath(rawPath);
    }

    private static String normalizeConfiguredPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        boolean trailingSlash = rawPath.endsWith("/");
        ArrayDeque<String> segments = new ArrayDeque<>();
        for (String part : rawPath.split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(part);
        }
        String normalized = "/" + String.join("/", segments);
        if (normalized.isBlank()) {
            normalized = "/";
        }
        if (trailingSlash && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private static boolean isRestrictedPath(String requestPath) {
        String upper = requestPath.toUpperCase(Locale.ROOT);
        return "/WEB-INF".equals(upper)
                || upper.startsWith("/WEB-INF/")
                || "/META-INF".equals(upper)
                || upper.startsWith("/META-INF/");
    }

    private static boolean looksLikeAsset(String requestPath) {
        int lastSlash = requestPath.lastIndexOf('/');
        String leaf = lastSlash >= 0 ? requestPath.substring(lastSlash + 1) : requestPath;
        return leaf.contains(".");
    }

    private static boolean supportsPrecompression(String requestPath) {
        String lower = requestPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".br") || lower.endsWith(".gz")) {
            return false;
        }
        return List.of(".css", ".html", ".js", ".json", ".map", ".mjs", ".svg", ".txt", ".wasm", ".xml")
                .stream()
                .anyMatch(lower::endsWith);
    }

    private static boolean acceptsEncoding(String acceptEncoding, String encoding) {
        if (acceptEncoding == null || acceptEncoding.isBlank()) {
            return false;
        }
        for (String value : acceptEncoding.split(",")) {
            String token = value.trim().toLowerCase(Locale.ROOT);
            if (token.equals(encoding) || token.startsWith(encoding + ";")) {
                return !token.contains("q=0");
            }
            if ("*".equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFingerprintedAsset(String logicalPath) {
        return FINGERPRINT_PATTERN.matcher(logicalPath).matches();
    }

    private static boolean isHtml(String logicalPath) {
        String lower = logicalPath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private static String resolveContentType(String logicalPath) {
        String lower = logicalPath.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : MIME_TYPES.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "application/octet-stream";
    }

    private static String formatHttpDate(long epochMillis) {
        return HTTP_DATE_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC));
    }

    private static String buildEtag(ResourceMetadata metadata) {
        return "\"%x-%x\"".formatted(metadata.length(), metadata.lastModifiedMillis());
    }

    private String readConfig(String name) {
        String servletValue = getInitParameter(name);
        if (servletValue != null && !servletValue.isBlank()) {
            return servletValue.trim();
        }
        String contextValue = getServletContext().getInitParameter(name);
        return contextValue == null || contextValue.isBlank() ? null : contextValue.trim();
    }

    private int parseIntConfig(String name, int defaultValue) {
        String rawValue = readConfig(name);
        if (rawValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            log.warn("Invalid integer config {}={}, using default {}", name, rawValue, defaultValue);
            return defaultValue;
        }
    }

    private boolean parseBooleanConfig(String name, boolean defaultValue) {
        String rawValue = readConfig(name);
        return rawValue == null ? defaultValue : Boolean.parseBoolean(rawValue);
    }

    private static ByteRange parseRange(String rangeHeader, long contentLength) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null;
        }
        String rawRange = rangeHeader.substring("bytes=".length()).trim();
        if (rawRange.contains(",")) {
            return null;
        }
        int dash = rawRange.indexOf('-');
        if (dash < 0) {
            return null;
        }

        String startToken = rawRange.substring(0, dash).trim();
        String endToken = rawRange.substring(dash + 1).trim();

        try {
            long start;
            long end;
            if (startToken.isEmpty()) {
                long suffixLength = Long.parseLong(endToken);
                if (suffixLength <= 0) {
                    return null;
                }
                start = Math.max(0, contentLength - suffixLength);
                end = contentLength - 1;
            } else {
                start = Long.parseLong(startToken);
                end = endToken.isEmpty() ? contentLength - 1 : Long.parseLong(endToken);
            }

            if (start < 0 || end < start || start >= contentLength) {
                return null;
            }
            end = Math.min(end, contentLength - 1);
            return new ByteRange(start, end);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record ResourceMetadata(long length, long lastModifiedMillis) {
    }

    private record ResolvedStaticResource(Path path, String logicalPath, String contentEncoding) {
    }

    private record ByteRange(long start, long end) {
        private int length() {
            return Math.toIntExact(end - start + 1);
        }
    }
}
