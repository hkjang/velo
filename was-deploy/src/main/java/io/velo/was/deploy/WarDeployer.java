package io.velo.was.deploy;

import io.velo.was.classloader.WebAppClassLoader;
import io.velo.was.servlet.ErrorPageSpec;
import io.velo.was.servlet.FilterRegistrationSpec;
import io.velo.was.servlet.ServletApplication;
import io.velo.was.servlet.SimpleServletApplication;
import io.velo.was.servlet.StaticResourceServlet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashSet;
import java.util.jar.JarFile;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        String normalizedContextPath = normalizeContextPath(contextPath);
        Path appRoot;
        boolean extracted = false;

        if (Files.isDirectory(source)) {
            if (!WarExtractor.isExplodedWar(source)) {
                throw new DeploymentException("Directory is not an exploded WAR: " + source);
            }
            appRoot = source;
        } else if (source.getFileName().toString().endsWith(".war")) {
            String appName = deriveAppName(source);
            appRoot = workDirectory.resolve(deriveExtractionDirectoryName(appName, normalizedContextPath));
            WarExtractor.extract(source, appRoot);
            extracted = true;
        } else {
            throw new DeploymentException("Unsupported deployment source: " + source);
        }

        Path webXmlPath = appRoot.resolve("WEB-INF").resolve("web.xml");
        WebXmlDescriptor descriptor = WebXmlParser.parse(webXmlPath);

        String appName = descriptor.displayName() != null ? descriptor.displayName() : deriveAppName(source);

        WebAppClassLoader classLoader = WebAppClassLoader.create(appName, appRoot, getClass().getClassLoader());
        AnnotationScanResult annotations = descriptor.metadataComplete()
                ? AnnotationScanResult.empty()
                : scanAnnotations(appRoot, classLoader, descriptor);

        SimpleServletApplication.Builder appBuilder = SimpleServletApplication.builder(appName, normalizedContextPath)
                .classLoader(classLoader)
                .initParameter("io.velo.was.webAppRoot", appRoot.toAbsolutePath().toString())
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
            WebXmlDescriptor.ServletDef servletDef = descriptor.servletByName(mapping.servletName());
            if (servletDef == null) {
                throw new DeploymentException("Missing servlet definition for mapping: " + mapping.servletName());
            }
            String urlPattern = normalizeUrlPattern(mapping.urlPattern());
            appBuilder.servlet(urlPattern, mapping.servletName(), servlet, servletDef.initParams());
            log.debug("app={} mapped servlet {} -> {}", appName, mapping.servletName(), urlPattern);
        }
        for (AnnotatedServlet annotatedServlet : annotations.servlets()) {
            servletInstances.put(annotatedServlet.name(), annotatedServlet.servlet());
            for (String urlPattern : annotatedServlet.urlPatterns()) {
                appBuilder.servlet(normalizeUrlPattern(urlPattern),
                        annotatedServlet.name(),
                        annotatedServlet.servlet(),
                        annotatedServlet.initParams());
                log.debug("app={} mapped annotated servlet {} -> {}",
                        appName, annotatedServlet.name(), urlPattern);
            }
        }

        boolean hasDefaultServlet = descriptor.servletMappings().stream()
                .map(WebXmlDescriptor.ServletMapping::urlPattern)
                .map(WarDeployer::normalizeUrlPattern)
                .anyMatch("/"::equals)
                || annotations.servlets().stream()
                .flatMap(servlet -> servlet.urlPatterns().stream())
                .map(WarDeployer::normalizeUrlPattern)
                .anyMatch("/"::equals);
        if (!hasDefaultServlet) {
            appBuilder.servlet("/", "VeloDefaultStaticServlet", new StaticResourceServlet());
            log.debug("app={} registered default static resource servlet", appName);
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
            WebXmlDescriptor.FilterDef filterDef = descriptor.filterByName(mapping.filterName());
            EnumSet<DispatcherType> types = parseDispatcherTypes(mapping.dispatchers());
            Map<String, String> initParameters = filterDef == null ? Map.of() : filterDef.initParams();
            if (mapping.isUrlPatternMapping()) {
                appBuilder.filter(mapping.filterName(), mapping.urlPattern(), filter,
                        initParameters,
                        types.toArray(DispatcherType[]::new));
                log.debug("app={} mapped filter {} -> {} dispatchers={}",
                        appName, mapping.filterName(), mapping.urlPattern(), types);
            } else if (mapping.isServletNameMapping()) {
                appBuilder.filterForServlet(mapping.filterName(), mapping.servletName(), filter,
                        initParameters,
                        types.toArray(DispatcherType[]::new));
                log.debug("app={} mapped filter {} -> servlet-name:{} dispatchers={}",
                        appName, mapping.filterName(), mapping.servletName(), types);
            }
        }
        for (AnnotatedFilter annotatedFilter : annotations.filters()) {
            filterInstances.put(annotatedFilter.name(), annotatedFilter.filter());
            for (String urlPattern : annotatedFilter.urlPatterns()) {
                appBuilder.filter(annotatedFilter.name(),
                        normalizeUrlPattern(urlPattern),
                        annotatedFilter.filter(),
                        annotatedFilter.initParams(),
                        annotatedFilter.dispatcherTypes().toArray(DispatcherType[]::new));
                log.debug("app={} mapped annotated filter {} -> {} dispatchers={}",
                        appName, annotatedFilter.name(), urlPattern, annotatedFilter.dispatcherTypes());
            }
            for (String servletName : annotatedFilter.servletNames()) {
                appBuilder.filterForServlet(annotatedFilter.name(),
                        servletName,
                        annotatedFilter.filter(),
                        annotatedFilter.initParams(),
                        annotatedFilter.dispatcherTypes().toArray(DispatcherType[]::new));
                log.debug("app={} mapped annotated filter {} -> servlet-name:{} dispatchers={}",
                        appName, annotatedFilter.name(), servletName, annotatedFilter.dispatcherTypes());
            }
        }

        // Register welcome files from web.xml
        if (!descriptor.welcomeFiles().isEmpty()) {
            appBuilder.welcomeFiles(descriptor.welcomeFiles());
            log.debug("app={} welcome files: {}", appName, descriptor.welcomeFiles());
        }

        for (WebXmlDescriptor.ErrorPageDef errorPage : descriptor.errorPages()) {
            if (errorPage.errorCode() != null) {
                appBuilder.errorPage(errorPage.errorCode(), errorPage.location());
            } else if (errorPage.exceptionType() != null) {
                Class<? extends Throwable> exceptionType =
                        instantiateExceptionType(classLoader, errorPage.exceptionType());
                appBuilder.errorPage(exceptionType, errorPage.location());
            }
        }

        // Instantiate and register listeners
        for (String listenerClass : descriptor.listenerClasses()) {
            EventListener listener = instantiate(classLoader, listenerClass, EventListener.class);
            registerListener(appBuilder, listener);
            log.debug("app={} registered listener {}", appName, listenerClass);
        }
        for (String listenerClass : annotations.listeners()) {
            EventListener listener = instantiate(classLoader, listenerClass, EventListener.class);
            registerListener(appBuilder, listener);
            log.debug("app={} registered annotated listener {}", appName, listenerClass);
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

    private static String deriveExtractionDirectoryName(String appName, String contextPath) {
        String suffix = contextPath == null || contextPath.isBlank() || "/".equals(contextPath)
                ? "ROOT"
                : contextPath.replace('/', '_');
        return appName + "__" + suffix;
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

    private static AnnotationScanResult scanAnnotations(Path appRoot,
                                                        ClassLoader classLoader,
                                                        WebXmlDescriptor descriptor) throws IOException {
        Set<String> existingServletClasses = descriptor.servlets().stream()
                .map(WebXmlDescriptor.ServletDef::className)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> existingServletNames = descriptor.servlets().stream()
                .map(WebXmlDescriptor.ServletDef::name)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> existingServletMappings = descriptor.servletMappings().stream()
                .map(WebXmlDescriptor.ServletMapping::urlPattern)
                .map(WarDeployer::normalizeUrlPattern)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingFilterClasses = descriptor.filters().stream()
                .map(WebXmlDescriptor.FilterDef::className)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> existingFilterNames = descriptor.filters().stream()
                .map(WebXmlDescriptor.FilterDef::name)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> existingListenerClasses = new LinkedHashSet<>(descriptor.listenerClasses());

        List<AnnotatedServlet> discoveredServlets = new ArrayList<>();
        List<AnnotatedFilter> discoveredFilters = new ArrayList<>();
        List<String> discoveredListeners = new ArrayList<>();

        for (String className : discoverClassNames(appRoot)) {
            try {
                Class<?> candidate = classLoader.loadClass(className);

                WebServlet webServlet = candidate.getAnnotation(WebServlet.class);
                if (webServlet != null && Servlet.class.isAssignableFrom(candidate)) {
                    AnnotatedServlet servlet = toAnnotatedServlet(candidate, webServlet, classLoader,
                            existingServletClasses, existingServletNames, existingServletMappings);
                    if (servlet != null) {
                        discoveredServlets.add(servlet);
                        existingServletClasses.add(className);
                        existingServletNames.add(servlet.name());
                        servlet.urlPatterns().stream()
                                .map(WarDeployer::normalizeUrlPattern)
                                .forEach(existingServletMappings::add);
                    }
                }

                WebFilter webFilter = candidate.getAnnotation(WebFilter.class);
                if (webFilter != null && Filter.class.isAssignableFrom(candidate)) {
                    AnnotatedFilter filter = toAnnotatedFilter(candidate, webFilter, classLoader,
                            existingFilterClasses, existingFilterNames);
                    if (filter != null) {
                        discoveredFilters.add(filter);
                        existingFilterClasses.add(className);
                        existingFilterNames.add(filter.name());
                    }
                }

                if (candidate.isAnnotationPresent(WebListener.class)
                        && EventListener.class.isAssignableFrom(candidate)
                        && existingListenerClasses.add(className)) {
                    discoveredListeners.add(className);
                }
            } catch (ClassNotFoundException | LinkageError | DeploymentException exception) {
                log.debug("Skipping annotation scan for class {} due to load failure: {}", className, exception.toString());
            }
        }

        log.info("Annotation scan completed: servlets={}, filters={}, listeners={}",
                discoveredServlets.size(), discoveredFilters.size(), discoveredListeners.size());
        return new AnnotationScanResult(discoveredServlets, discoveredFilters, discoveredListeners);
    }

    private static Set<String> discoverClassNames(Path appRoot) throws IOException {
        Set<String> classNames = new LinkedHashSet<>();
        Path classesDir = appRoot.resolve("WEB-INF").resolve("classes");
        if (Files.isDirectory(classesDir)) {
            try (var walk = Files.walk(classesDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".class"))
                        .forEach(path -> classNames.add(toClassName(classesDir, path)));
            }
        }

        Path libDir = appRoot.resolve("WEB-INF").resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(libDir, "*.jar")) {
                for (Path jar : jars) {
                    try (JarFile jarFile = new JarFile(jar.toFile())) {
                        jarFile.stream()
                                .filter(entry -> !entry.isDirectory())
                                .map(java.util.jar.JarEntry::getName)
                                .filter(name -> name.endsWith(".class"))
                                .filter(name -> !name.startsWith("META-INF/versions/"))
                                .forEach(name -> classNames.add(name.substring(0, name.length() - 6).replace('/', '.')));
                    }
                }
            }
        }
        return classNames;
    }

    private static String toClassName(Path classesDir, Path classFile) {
        String relative = classesDir.relativize(classFile).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - 6).replace('/', '.');
    }

    private static AnnotatedServlet toAnnotatedServlet(Class<?> candidate,
                                                       WebServlet annotation,
                                                       ClassLoader classLoader,
                                                       Set<String> existingServletClasses,
                                                       Set<String> existingServletNames,
                                                       Set<String> existingServletMappings) throws DeploymentException {
        String className = candidate.getName();
        String servletName = annotation.name().isBlank() ? candidate.getSimpleName() : annotation.name().trim();
        if (existingServletClasses.contains(className) || existingServletNames.contains(servletName)) {
            return null;
        }

        List<String> urlPatterns = annotationValues(annotation.value(), annotation.urlPatterns());
        if (urlPatterns.isEmpty()) {
            return null;
        }

        List<String> effectivePatterns = urlPatterns.stream()
                .map(WarDeployer::normalizeUrlPattern)
                .filter(pattern -> !existingServletMappings.contains(pattern))
                .distinct()
                .toList();
        if (effectivePatterns.isEmpty()) {
            return null;
        }

        Servlet servlet = instantiate(classLoader, className, Servlet.class);
        return new AnnotatedServlet(servletName, className, servlet, initParams(annotation.initParams()), effectivePatterns);
    }

    private static AnnotatedFilter toAnnotatedFilter(Class<?> candidate,
                                                     WebFilter annotation,
                                                     ClassLoader classLoader,
                                                     Set<String> existingFilterClasses,
                                                     Set<String> existingFilterNames) throws DeploymentException {
        String className = candidate.getName();
        String filterName = annotation.filterName().isBlank() ? candidate.getSimpleName() : annotation.filterName().trim();
        if (existingFilterClasses.contains(className) || existingFilterNames.contains(filterName)) {
            return null;
        }

        List<String> urlPatterns = annotationValues(annotation.value(), annotation.urlPatterns());
        List<String> servletNames = annotationValues(annotation.servletNames(), null);
        if (urlPatterns.isEmpty() && servletNames.isEmpty()) {
            return null;
        }
        urlPatterns = urlPatterns.stream().distinct().toList();
        servletNames = servletNames.stream().distinct().toList();

        Filter filter = instantiate(classLoader, className, Filter.class);
        EnumSet<DispatcherType> dispatchers = annotation.dispatcherTypes().length == 0
                ? EnumSet.of(DispatcherType.REQUEST)
                : EnumSet.copyOf(List.of(annotation.dispatcherTypes()));
        return new AnnotatedFilter(filterName, className, filter,
                initParams(annotation.initParams()),
                urlPatterns,
                servletNames,
                dispatchers);
    }

    private static List<String> annotationValues(String[] primary, String[] secondary) {
        List<String> values = new ArrayList<>();
        addNonBlank(values, primary);
        addNonBlank(values, secondary);
        return values;
    }

    private static void addNonBlank(Collection<String> target, String[] values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim());
            }
        }
    }

    private static Map<String, String> initParams(WebInitParam[] initParams) {
        Map<String, String> values = new LinkedHashMap<>();
        if (initParams == null) {
            return values;
        }
        for (WebInitParam initParam : initParams) {
            if (initParam.name() != null && !initParam.name().isBlank()) {
                values.put(initParam.name().trim(), initParam.value());
            }
        }
        return values;
    }

    private static void registerListener(SimpleServletApplication.Builder appBuilder, EventListener listener) {
        if (listener instanceof ServletContextListener scl) {
            appBuilder.servletContextListener(scl);
        }
        if (listener instanceof ServletContextAttributeListener scal) {
            appBuilder.servletContextAttributeListener(scal);
        }
        if (listener instanceof ServletRequestListener srl) {
            appBuilder.servletRequestListener(srl);
        }
        if (listener instanceof HttpSessionListener hsl) {
            appBuilder.httpSessionListener(hsl);
        }
        if (listener instanceof HttpSessionAttributeListener hsal) {
            appBuilder.httpSessionAttributeListener(hsal);
        }
        if (listener instanceof HttpSessionIdListener hsil) {
            appBuilder.httpSessionIdListener(hsil);
        }
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

    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable> instantiateExceptionType(ClassLoader classLoader, String className)
            throws DeploymentException {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (!Throwable.class.isAssignableFrom(clazz)) {
                throw new DeploymentException(className + " is not a Throwable type");
            }
            return (Class<? extends Throwable>) clazz;
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException("Failed to resolve exception type " + className + ": " + e.getMessage(), e);
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

    private record AnnotationScanResult(
            List<AnnotatedServlet> servlets,
            List<AnnotatedFilter> filters,
            List<String> listeners
    ) {
        private static AnnotationScanResult empty() {
            return new AnnotationScanResult(List.of(), List.of(), List.of());
        }
    }

    private record AnnotatedServlet(
            String name,
            String className,
            Servlet servlet,
            Map<String, String> initParams,
            List<String> urlPatterns
    ) {
    }

    private record AnnotatedFilter(
            String name,
            String className,
            Filter filter,
            Map<String, String> initParams,
            List<String> urlPatterns,
            List<String> servletNames,
            EnumSet<DispatcherType> dispatcherTypes
    ) {
    }
}
