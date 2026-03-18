package io.velo.was.servlet;

import jakarta.servlet.Servlet;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class SimpleServletApplication implements ServletApplication {

    private final String name;
    private final String contextPath;
    private final ClassLoader classLoader;
    private final Map<String, Servlet> servlets;
    private final Map<String, String> servletNames;
    private final Map<String, Map<String, String>> servletInitParameters;
    private final List<FilterRegistrationSpec> filters;
    private final List<ServletContextListener> servletContextListeners;
    private final List<ServletContextAttributeListener> servletContextAttributeListeners;
    private final List<ServletRequestListener> servletRequestListeners;
    private final List<HttpSessionListener> httpSessionListeners;
    private final List<HttpSessionAttributeListener> httpSessionAttributeListeners;
    private final List<HttpSessionIdListener> httpSessionIdListeners;
    private final List<ErrorPageSpec> errorPages;
    private final Map<String, String> initParameters;
    private final List<String> welcomeFiles;

    public SimpleServletApplication(String name,
                                    String contextPath,
                                    ClassLoader classLoader,
                                    Map<String, Servlet> servlets,
                                    Map<String, String> servletNames,
                                    Map<String, Map<String, String>> servletInitParameters,
                                    List<FilterRegistrationSpec> filters,
                                    List<ServletContextListener> servletContextListeners,
                                    List<ServletContextAttributeListener> servletContextAttributeListeners,
                                    List<ServletRequestListener> servletRequestListeners,
                                    List<HttpSessionListener> httpSessionListeners,
                                    List<HttpSessionAttributeListener> httpSessionAttributeListeners,
                                    List<HttpSessionIdListener> httpSessionIdListeners,
                                    List<ErrorPageSpec> errorPages,
                                    Map<String, String> initParameters) {
        this(name, contextPath, classLoader, servlets, servletNames, servletInitParameters, filters, servletContextListeners,
                servletContextAttributeListeners, servletRequestListeners, httpSessionListeners,
                httpSessionAttributeListeners, httpSessionIdListeners, errorPages, initParameters, List.of());
    }

    public SimpleServletApplication(String name,
                                    String contextPath,
                                    ClassLoader classLoader,
                                    Map<String, Servlet> servlets,
                                    Map<String, String> servletNames,
                                    Map<String, Map<String, String>> servletInitParameters,
                                    List<FilterRegistrationSpec> filters,
                                    List<ServletContextListener> servletContextListeners,
                                    List<ServletContextAttributeListener> servletContextAttributeListeners,
                                    List<ServletRequestListener> servletRequestListeners,
                                    List<HttpSessionListener> httpSessionListeners,
                                    List<HttpSessionAttributeListener> httpSessionAttributeListeners,
                                    List<HttpSessionIdListener> httpSessionIdListeners,
                                    List<ErrorPageSpec> errorPages,
                                    Map<String, String> initParameters,
                                    List<String> welcomeFiles) {
        this.name = name;
        this.contextPath = normalizeContextPath(contextPath);
        this.classLoader = classLoader;
        this.servlets = new LinkedHashMap<>(servlets);
        this.servletNames = new LinkedHashMap<>(servletNames);
        this.servletInitParameters = copyServletInitParameters(servletInitParameters);
        this.filters = new ArrayList<>(filters);
        this.servletContextListeners = new ArrayList<>(servletContextListeners);
        this.servletContextAttributeListeners = new ArrayList<>(servletContextAttributeListeners);
        this.servletRequestListeners = new ArrayList<>(servletRequestListeners);
        this.httpSessionListeners = new ArrayList<>(httpSessionListeners);
        this.httpSessionAttributeListeners = new ArrayList<>(httpSessionAttributeListeners);
        this.httpSessionIdListeners = new ArrayList<>(httpSessionIdListeners);
        this.errorPages = new ArrayList<>(errorPages);
        this.initParameters = new LinkedHashMap<>(initParameters);
        this.welcomeFiles = new ArrayList<>(welcomeFiles);
    }

    public static Builder builder(String name, String contextPath) {
        return new Builder(name, contextPath);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String contextPath() {
        return contextPath;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public Map<String, Servlet> servlets() {
        return Map.copyOf(servlets);
    }

    @Override
    public String servletName(String mappingPath) {
        return servletNames.getOrDefault(mappingPath, mappingPath);
    }

    @Override
    public Map<String, String> servletInitParameters(String mappingPath) {
        Map<String, String> parameters = servletInitParameters.get(mappingPath);
        return parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    @Override
    public List<FilterRegistrationSpec> filters() {
        return List.copyOf(filters);
    }

    @Override
    public List<ServletContextListener> servletContextListeners() {
        return List.copyOf(servletContextListeners);
    }

    @Override
    public List<ServletContextAttributeListener> servletContextAttributeListeners() {
        return List.copyOf(servletContextAttributeListeners);
    }

    @Override
    public List<ServletRequestListener> servletRequestListeners() {
        return List.copyOf(servletRequestListeners);
    }

    @Override
    public List<HttpSessionListener> httpSessionListeners() {
        return List.copyOf(httpSessionListeners);
    }

    @Override
    public List<HttpSessionAttributeListener> httpSessionAttributeListeners() {
        return List.copyOf(httpSessionAttributeListeners);
    }

    @Override
    public List<HttpSessionIdListener> httpSessionIdListeners() {
        return List.copyOf(httpSessionIdListeners);
    }

    @Override
    public List<ErrorPageSpec> errorPages() {
        return List.copyOf(errorPages);
    }

    @Override
    public Map<String, String> initParameters() {
        return Map.copyOf(initParameters);
    }

    @Override
    public List<String> welcomeFiles() {
        return List.copyOf(welcomeFiles);
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }

    private static Map<String, Map<String, String>> copyServletInitParameters(
            Map<String, Map<String, String>> servletInitParameters) {
        Map<String, Map<String, String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : servletInitParameters.entrySet()) {
            copied.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copied;
    }

    public static final class Builder {
        private final String name;
        private final String contextPath;
        private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        private final Map<String, Servlet> servlets = new LinkedHashMap<>();
        private final Map<String, String> servletNames = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> servletInitParameters = new LinkedHashMap<>();
        private final List<FilterRegistrationSpec> filters = new ArrayList<>();
        private final List<ServletContextListener> servletContextListeners = new ArrayList<>();
        private final List<ServletContextAttributeListener> servletContextAttributeListeners = new ArrayList<>();
        private final List<ServletRequestListener> servletRequestListeners = new ArrayList<>();
        private final List<HttpSessionListener> httpSessionListeners = new ArrayList<>();
        private final List<HttpSessionAttributeListener> httpSessionAttributeListeners = new ArrayList<>();
        private final List<HttpSessionIdListener> httpSessionIdListeners = new ArrayList<>();
        private final List<ErrorPageSpec> errorPages = new ArrayList<>();
        private final Map<String, String> initParameters = new LinkedHashMap<>();
        private final List<String> welcomeFiles = new ArrayList<>();

        private Builder(String name, String contextPath) {
            this.name = name;
            this.contextPath = contextPath;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder servlet(String path, Servlet servlet) {
            return servlet(path, normalizeServletPath(path), servlet, Map.of());
        }

        public Builder servlet(String path, String servletName, Servlet servlet) {
            return servlet(path, servletName, servlet, Map.of());
        }

        public Builder servlet(String path, String servletName, Servlet servlet, Map<String, String> initParameters) {
            String normalizedPath = normalizeServletPath(path);
            servlets.put(normalizedPath, servlet);
            servletNames.put(normalizedPath, servletName);
            servletInitParameters.put(normalizedPath, new LinkedHashMap<>(initParameters));
            return this;
        }

        public Builder initParameter(String name, String value) {
            initParameters.put(name, value);
            return this;
        }

        public Builder filter(Filter filter) {
            filters.add(new FilterRegistrationSpec("/*", filter, EnumSet.allOf(DispatcherType.class)));
            return this;
        }

        public Builder filter(String pathPattern, Filter filter, DispatcherType... dispatcherTypes) {
            EnumSet<DispatcherType> types = dispatcherTypes == null || dispatcherTypes.length == 0
                    ? EnumSet.allOf(DispatcherType.class)
                    : EnumSet.copyOf(List.of(dispatcherTypes));
            filters.add(new FilterRegistrationSpec(pathPattern, filter, types));
            return this;
        }

        public Builder servletContextListener(ServletContextListener listener) {
            servletContextListeners.add(listener);
            return this;
        }

        public Builder servletContextAttributeListener(ServletContextAttributeListener listener) {
            servletContextAttributeListeners.add(listener);
            return this;
        }

        public Builder servletRequestListener(ServletRequestListener listener) {
            servletRequestListeners.add(listener);
            return this;
        }

        public Builder httpSessionListener(HttpSessionListener listener) {
            httpSessionListeners.add(listener);
            return this;
        }

        public Builder httpSessionAttributeListener(HttpSessionAttributeListener listener) {
            httpSessionAttributeListeners.add(listener);
            return this;
        }

        public Builder httpSessionIdListener(HttpSessionIdListener listener) {
            httpSessionIdListeners.add(listener);
            return this;
        }

        public Builder errorPage(int statusCode, String location) {
            errorPages.add(new ErrorPageSpec(statusCode, null, normalizeServletPath(location)));
            return this;
        }

        public Builder errorPage(Class<? extends Throwable> exceptionType, String location) {
            errorPages.add(new ErrorPageSpec(null, exceptionType, normalizeServletPath(location)));
            return this;
        }

        public Builder welcomeFile(String welcomeFile) {
            welcomeFiles.add(welcomeFile);
            return this;
        }

        public Builder welcomeFiles(List<String> welcomeFiles) {
            this.welcomeFiles.addAll(welcomeFiles);
            return this;
        }

        public SimpleServletApplication build() {
            return new SimpleServletApplication(
                    name,
                    contextPath,
                    classLoader,
                    servlets,
                    servletNames,
                    servletInitParameters,
                    filters,
                    servletContextListeners,
                    servletContextAttributeListeners,
                    servletRequestListeners,
                    httpSessionListeners,
                    httpSessionAttributeListeners,
                    httpSessionIdListeners,
                    errorPages,
                    initParameters,
                    welcomeFiles);
        }

        private static String normalizeServletPath(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "/";
            }
            return path.startsWith("/") || path.startsWith("*.") ? path : "/" + path;
        }
    }
}
