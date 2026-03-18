package io.velo.was.bootstrap;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.servlet.InMemoryHttpSessionStore;
import io.velo.was.servlet.SessionState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class AdminWarUploadService {

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";
    private static final String AUTHENTICATED_ATTR = "velo.admin.authenticated";
    private static final String CSRF_TOKEN_ATTR = "velo.csrf.token";
    private static final String CSRF_HEADER = "X-CSRF-Token";

    private final String uploadPath;
    private final ServerConfiguration configuration;
    private final InMemoryHttpSessionStore sessionStore;
    private final UploadDeployer uploadDeployer;

    AdminWarUploadService(ServerConfiguration configuration,
                          InMemoryHttpSessionStore sessionStore,
                          UploadDeployer uploadDeployer) {
        this.configuration = configuration;
        this.sessionStore = sessionStore;
        this.uploadDeployer = uploadDeployer;
        this.uploadPath = normalizeContextPath(configuration.getServer().getWebAdmin().getContextPath()) + "/upload-war";
    }

    boolean matches(HttpRequest request) {
        return "POST".equalsIgnoreCase(request.method().name())
                && uploadPath.equals(new QueryStringDecoder(request.uri()).path());
    }

    AuthorizationResult authorize(HttpRequest request) {
        SessionState session = resolveSession(request.headers().get(HttpHeaderNames.COOKIE));
        if (session == null || !Boolean.TRUE.equals(session.attributes().get(AUTHENTICATED_ATTR))) {
            return new AuthorizationResult(false, 401, "Authentication required");
        }
        String sessionToken = stringValue(session.attributes().get(CSRF_TOKEN_ATTR));
        String requestToken = request.headers().get(CSRF_HEADER);
        if (sessionToken == null || requestToken == null || !sessionToken.equals(requestToken)) {
            return new AuthorizationResult(false, 403, "CSRF token validation failed");
        }
        session.touch();
        return new AuthorizationResult(true, 200, "");
    }

    UploadResult handleUploadedWar(Path stagedFile, String submittedFileName, String contextPath) throws Exception {
        String sanitizedFileName = sanitizeFilename(submittedFileName);
        if (sanitizedFileName == null || !sanitizedFileName.toLowerCase().endsWith(".war")) {
            throw new IllegalArgumentException("Only .war files are accepted");
        }

        Path deployDirectory = Path.of(configuration.getServer().getDeploy().getDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(deployDirectory);

        String normalizedContextPath = normalizeOptionalContextPath(contextPath);
        if (normalizedContextPath != null) {
            Path manualDirectory = deployDirectory.resolve(".uploads");
            Files.createDirectories(manualDirectory);
            Path storedFile = manualDirectory.resolve(uniqueFileName(sanitizedFileName));
            Files.move(stagedFile, storedFile, StandardCopyOption.REPLACE_EXISTING);
            uploadDeployer.deploy(storedFile, normalizedContextPath);
            return new UploadResult(200, true,
                    "WAR file '%s' uploaded and deployed to context path '%s'".formatted(
                            sanitizedFileName, normalizedContextPath));
        }

        Path targetFile = deployDirectory.resolve(sanitizedFileName);
        Files.move(stagedFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        if (configuration.getServer().getDeploy().isHotDeploy()) {
            return new UploadResult(200, true,
                    "WAR file '%s' uploaded to deploy directory and hot deploy will apply it".formatted(
                            sanitizedFileName));
        }

        String derivedContextPath = deriveDefaultContextPath(sanitizedFileName);
        uploadDeployer.deploy(targetFile, derivedContextPath);
        return new UploadResult(200, true,
                "WAR file '%s' uploaded and deployed successfully".formatted(sanitizedFileName));
    }

    private SessionState resolveSession(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String cookiePart : cookieHeader.split(";")) {
            String[] pair = cookiePart.trim().split("=", 2);
            if (pair.length == 2 && SESSION_COOKIE_NAME.equals(pair[0].trim())) {
                return sessionStore.find(pair[1].trim());
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String sanitizeFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        return Path.of(fileName).getFileName().toString();
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }

    private static String normalizeOptionalContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank()) {
            return null;
        }
        String normalized = contextPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String deriveDefaultContextPath(String fileName) {
        if ("ROOT.war".equalsIgnoreCase(fileName)) {
            return "";
        }
        int extensionIndex = fileName.toLowerCase().lastIndexOf(".war");
        String appName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
        return normalizeContextPath(appName);
    }

    private static String uniqueFileName(String originalFileName) {
        return UUID.randomUUID().toString().replace("-", "") + "-" + originalFileName;
    }

    record AuthorizationResult(boolean allowed, int statusCode, String message) {
    }

    record UploadResult(int statusCode, boolean success, String message) {
    }

    @FunctionalInterface
    interface UploadDeployer {
        void deploy(Path warPath, String contextPath) throws Exception;
    }
}
