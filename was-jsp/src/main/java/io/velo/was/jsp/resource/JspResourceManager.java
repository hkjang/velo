package io.velo.was.jsp.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages JSP resource resolution: JSP files, include files, TLD descriptors, tag files.
 * Resolves resources from the web application root directory.
 */
public class JspResourceManager {

    private static final Logger log = LoggerFactory.getLogger(JspResourceManager.class);

    private final Path webAppRoot;
    private final Map<String, String> sourceCache = new ConcurrentHashMap<>();

    public JspResourceManager(Path webAppRoot) {
        this.webAppRoot = webAppRoot;
    }

    /**
     * Reads the JSP source file content.
     */
    public String readJspSource(String jspPath) throws IOException {
        String cached = sourceCache.get(jspPath);
        if (cached != null) return cached;

        Path resolved = resolveJspPath(jspPath);
        if (resolved == null || !Files.exists(resolved)) {
            throw new IOException("JSP not found: " + jspPath);
        }
        String content = Files.readString(resolved, StandardCharsets.UTF_8);
        sourceCache.put(jspPath, content);
        return content;
    }

    /**
     * Returns the last modified time of the JSP file.
     */
    public long lastModified(String jspPath) {
        Path resolved = resolveJspPath(jspPath);
        if (resolved == null || !Files.exists(resolved)) return -1;
        try {
            return Files.getLastModifiedTime(resolved).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Checks if the JSP file exists.
     */
    public boolean exists(String jspPath) {
        Path resolved = resolveJspPath(jspPath);
        return resolved != null && Files.exists(resolved);
    }

    /**
     * Resolves a JSP path relative to the web application root.
     */
    public Path resolveJspPath(String jspPath) {
        if (jspPath == null) return null;
        String normalized = jspPath.startsWith("/") ? jspPath.substring(1) : jspPath;
        return webAppRoot.resolve(normalized).normalize();
    }

    /**
     * Finds TLD files in WEB-INF and META-INF.
     */
    public List<Path> findTldFiles() {
        var tldPaths = new java.util.ArrayList<Path>();
        try {
            Path webInf = webAppRoot.resolve("WEB-INF");
            if (Files.exists(webInf)) {
                try (var walk = Files.walk(webInf)) {
                    walk.filter(p -> p.toString().endsWith(".tld"))
                        .forEach(tldPaths::add);
                }
            }
            Path metaInf = webAppRoot.resolve("META-INF");
            if (Files.exists(metaInf)) {
                try (var walk = Files.walk(metaInf)) {
                    walk.filter(p -> p.toString().endsWith(".tld"))
                        .forEach(tldPaths::add);
                }
            }
        } catch (IOException e) {
            log.warn("Error scanning for TLD files: {}", e.getMessage());
        }
        return tldPaths;
    }

    /**
     * Invalidates a cached JSP source.
     */
    public void invalidateSource(String jspPath) {
        sourceCache.remove(jspPath);
    }

    /**
     * Clears the entire source cache.
     */
    public void clearCache() {
        sourceCache.clear();
    }

    public Path webAppRoot() {
        return webAppRoot;
    }
}
