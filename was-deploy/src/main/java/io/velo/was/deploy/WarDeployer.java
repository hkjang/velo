package io.velo.was.deploy;

import io.velo.was.classloader.WebAppClassLoader;
import io.velo.was.servlet.FilterRegistrationSpec;
import io.velo.was.servlet.ServletApplication;
import io.velo.was.servlet.SimpleServletApplication;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deploys a WAR file or exploded WAR directory into a {@link ServletApplication}.
 * <p>
 * The deployment flow:
 * <ol>
 *     <li>If WAR file: extract to temp directory</li>
 *     <li>Parse WEB-INF/web.xml</li>
 *     <li>Create isolated WebAppClassLoader</li>
 *     <li>Instantiate servlets, filters, listeners from web.xml</li>
 *     <li>Build and return a ServletApplication</li>
 * </ol>
 */
public class WarDeployer {

    private static final Logger log = LoggerFactory.getLogger(WarDeployer.class);

    private final Path workDirectory;

    /**
     * Creates a deployer with the given work directory for extracting WAR files.
     */
    public WarDeployer(Path workDirectory) {
        this.workDirectory = workDirectory;
    }

    /**
     * Deploys a WAR file or exploded directory.
     *
     * @param source      the WAR file or exploded directory path
     * @param contextPath the context path (e.g., "/myapp")
     * @return the deployment result containing the application and metadata
     * @throws Exception if deployment fails
     */
    public DeploymentResult deploy(Path source, String contextPath) throws Exception {
        Path appRoot;
        boolean extracted = false;

        if (Files.isDirectory(source)) {
            if (!WarExtractor.isExplodedWar(source)) {
                throw new DeploymentException("Directory is not an exploded WAR: " + source);
            }
            appRoot = source;
        } else if (source.getFileName().toString().endsWith(".war")) {
            String appName = deriveAppName(source);
            appRoot = workDirectory.resolve(appName);
            WarExtractor.extract(source, appRoot);
            extracted = true;
        } else {
            throw new DeploymentException("Unsupported deployment source: " + source);
        }

        Path webXmlPath = appRoot.resolve("WEB-INF").resolve("web.xml");
        WebXmlDescriptor descriptor = WebXmlParser.parse(webXmlPath);

        String appName = descriptor.displayName() != null ? descriptor.displayName() : deriveAppName(source);
        String normalizedContextPath = normalizeContextPath(contextPath);

        WebAppClassLoader classLoader = WebAppClassLoader.create(appName, appRoot, getClass().getClassLoader());

        SimpleServletApplication.Builder appBuilder = SimpleServletApplication.builder(appName, normalizedContextPath)
                .classLoader(classLoader)
                .initParameter("io.velo.was.jsp.webAppRoot", appRoot.toAbsolutePath().toString());

        // Add context parameters
        for (Map.Entry<String, String> param : descriptor.contextParams().entrySet()) {
            appBuilder.initParameter(param.getKey(), param.getValue());
        }

        // Instantiate and register servlets with their mappings
        Map<String, Servlet> servletInstances = new LinkedHashMap<>();
        for (WebXmlDescriptor.ServletDef servletDef : descriptor.servlets()) {
            Servlet servlet = instantiate(classLoader, servletDef.className(), Servlet.class);
            servletInstances.put(servletDef.name(), servlet);
        }

        for (WebXmlDescriptor.ServletMapping mapping : descriptor.servletMappings()) {
            Servlet servlet = servletInstances.get(mapping.servletName());
            if (servlet == null) {
                throw new DeploymentException("Servlet mapping references unknown servlet: " + mapping.servletName());
            }
            String urlPattern = normalizeUrlPattern(mapping.urlPattern());
            appBuilder.servlet(urlPattern, servlet);
            log.debug("app={} mapped servlet {} -> {}", appName, mapping.servletName(), urlPattern);
        }

        // Instantiate and register filters with their mappings
        Map<String, Filter> filterInstances = new LinkedHashMap<>();
        for (WebXmlDescriptor.FilterDef filterDef : descriptor.filters()) {
            Filter filter = instantiate(classLoader, filterDef.className(), Filter.class);
            filterInstances.put(filterDef.name(), filter);
        }

        for (WebXmlDescriptor.FilterMapping mapping : descriptor.filterMappings()) {
            Filter filter = filterInstances.get(mapping.filterName());
            if (filter == null) {
                throw new DeploymentException("Filter mapping references unknown filter: " + mapping.filterName());
            }
            EnumSet<DispatcherType> types = parseDispatcherTypes(mapping.dispatchers());
            appBuilder.filter(mapping.urlPattern(), filter, types.toArray(DispatcherType[]::new));
            log.debug("app={} mapped filter {} -> {} dispatchers={}", appName, mapping.filterName(), mapping.urlPattern(), types);
        }

        // Register welcome files from web.xml
        if (!descriptor.welcomeFiles().isEmpty()) {
            appBuilder.welcomeFiles(descriptor.welcomeFiles());
            log.debug("app={} welcome files: {}", appName, descriptor.welcomeFiles());
        }

        // Instantiate and register listeners
        for (String listenerClass : descriptor.listenerClasses()) {
            EventListener listener = instantiate(classLoader, listenerClass, EventListener.class);
            if (listener instanceof ServletContextListener scl) {
                appBuilder.servletContextListener(scl);
            }
            if (listener instanceof ServletRequestListener srl) {
                appBuilder.servletRequestListener(srl);
            }
            log.debug("app={} registered listener {}", appName, listenerClass);
        }

        ServletApplication application = appBuilder.build();
        log.info("WAR deployed: name={} contextPath={} source={} servlets={} filters={} listeners={}",
                appName, normalizedContextPath, source, servletInstances.size(),
                filterInstances.size(), descriptor.listenerClasses().size());

        return new DeploymentResult(application, appRoot, classLoader, descriptor, extracted);
    }

    /**
     * Cleans up the extracted WAR directory.
     */
    public void cleanup(DeploymentResult result) {
        if (result.extracted() && Files.exists(result.appRoot())) {
            try {
                deleteRecursively(result.appRoot());
                log.info("Cleaned up extracted WAR at {}", result.appRoot());
            } catch (IOException e) {
                log.warn("Failed to clean up extracted WAR at {}", result.appRoot(), e);
            }
        }
        if (result.classLoader() instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.warn("Failed to close classloader for {}", result.application().name(), e);
            }
        }
    }

    private static String deriveAppName(Path source) {
        String fileName = source.getFileName().toString();
        if (fileName.endsWith(".war")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        String path = contextPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String normalizeUrlPattern(String urlPattern) {
        if (urlPattern == null || urlPattern.isBlank()) {
            return "/";
        }
        String pattern = urlPattern.trim();
        // Strip wildcard suffixes for servlet path matching: /api/* -> /api
        if (pattern.endsWith("/*")) {
            pattern = pattern.substring(0, pattern.length() - 2);
        }
        if (pattern.isEmpty()) {
            return "/";
        }
        if (!pattern.startsWith("/") && !pattern.startsWith("*.")) {
            pattern = "/" + pattern;
        }
        return pattern;
    }

    private static EnumSet<DispatcherType> parseDispatcherTypes(List<String> dispatchers) {
        EnumSet<DispatcherType> types = EnumSet.noneOf(DispatcherType.class);
        for (String d : dispatchers) {
            try {
                types.add(DispatcherType.valueOf(d.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown dispatcher type: {}", d);
            }
        }
        return types.isEmpty() ? EnumSet.of(DispatcherType.REQUEST) : types;
    }

    @SuppressWarnings("unchecked")
    private static <T> T instantiate(ClassLoader classLoader, String className, Class<T> expectedType) throws DeploymentException {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (!expectedType.isAssignableFrom(clazz)) {
                throw new DeploymentException(className + " does not implement " + expectedType.getSimpleName());
            }
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException("Failed to instantiate " + className + ": " + e.getMessage(), e);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // best effort
                }
            });
        }
    }

    /**
     * The result of a WAR deployment.
     */
    public record DeploymentResult(
            ServletApplication application,
            Path appRoot,
            WebAppClassLoader classLoader,
            WebXmlDescriptor descriptor,
            boolean extracted
    ) {
    }
}
