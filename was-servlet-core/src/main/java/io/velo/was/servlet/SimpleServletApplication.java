package io.velo.was.servlet;

import jakarta.servlet.Servlet;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.DispatcherType;

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
    private final List<FilterRegistrationSpec> filters;
    private final List<ServletContextListener> servletContextListeners;
    private final List<ServletRequestListener> servletRequestListeners;
    private final Map<String, String> initParameters;

    public SimpleServletApplication(String name,
                                    String contextPath,
                                    ClassLoader classLoader,
                                    Map<String, Servlet> servlets,
                                    List<FilterRegistrationSpec> filters,
                                    List<ServletContextListener> servletContextListeners,
                                    List<ServletRequestListener> servletRequestListeners,
                                    Map<String, String> initParameters) {
        this.name = name;
        this.contextPath = normalizeContextPath(contextPath);
        this.classLoader = classLoader;
        this.servlets = new LinkedHashMap<>(servlets);
        this.filters = new ArrayList<>(filters);
        this.servletContextListeners = new ArrayList<>(servletContextListeners);
        this.servletRequestListeners = new ArrayList<>(servletRequestListeners);
        this.initParameters = new LinkedHashMap<>(initParameters);
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
    public List<FilterRegistrationSpec> filters() {
        return List.copyOf(filters);
    }

    @Override
    public List<ServletContextListener> servletContextListeners() {
        return List.copyOf(servletContextListeners);
    }

    @Override
    public List<ServletRequestListener> servletRequestListeners() {
        return List.copyOf(servletRequestListeners);
    }

    @Override
    public Map<String, String> initParameters() {
        return Map.copyOf(initParameters);
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }

    public static final class Builder {
        private final String name;
        private final String contextPath;
        private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        private final Map<String, Servlet> servlets = new LinkedHashMap<>();
        private final List<FilterRegistrationSpec> filters = new ArrayList<>();
        private final List<ServletContextListener> servletContextListeners = new ArrayList<>();
        private final List<ServletRequestListener> servletRequestListeners = new ArrayList<>();
        private final Map<String, String> initParameters = new LinkedHashMap<>();

        private Builder(String name, String contextPath) {
            this.name = name;
            this.contextPath = contextPath;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder servlet(String path, Servlet servlet) {
            servlets.put(normalizeServletPath(path), servlet);
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

        public Builder servletRequestListener(ServletRequestListener listener) {
            servletRequestListeners.add(listener);
            return this;
        }

        public SimpleServletApplication build() {
            return new SimpleServletApplication(
                    name,
                    contextPath,
                    classLoader,
                    servlets,
                    filters,
                    servletContextListeners,
                    servletRequestListeners,
                    initParameters);
        }

        private static String normalizeServletPath(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "/";
            }
            return path.startsWith("/") || path.startsWith("*.") ? path : "/" + path;
        }
    }
}
