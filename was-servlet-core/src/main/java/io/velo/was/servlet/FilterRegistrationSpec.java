package io.velo.was.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;

public record FilterRegistrationSpec(
        String filterName,
        String pathPattern,
        String servletName,
        Filter filter,
        Set<DispatcherType> dispatcherTypes,
        Map<String, String> initParameters
) {
    public FilterRegistrationSpec {
        if (filterName != null && filterName.isBlank()) {
            filterName = null;
        }
        if (pathPattern != null && pathPattern.isBlank()) {
            pathPattern = null;
        }
        if (servletName != null && servletName.isBlank()) {
            servletName = null;
        }
        if (pathPattern == null && servletName == null) {
            pathPattern = "/*";
        }
        if (pathPattern != null) {
            pathPattern = normalizePattern(pathPattern);
        }
        dispatcherTypes = dispatcherTypes == null || dispatcherTypes.isEmpty()
                ? EnumSet.allOf(DispatcherType.class)
                : EnumSet.copyOf(dispatcherTypes);
        initParameters = initParameters == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(initParameters));
    }

    public FilterRegistrationSpec(String pathPattern, Filter filter, Set<DispatcherType> dispatcherTypes) {
        this(null, pathPattern, null, filter, dispatcherTypes, Map.of());
    }

    public FilterRegistrationSpec(String filterName,
                                  String pathPattern,
                                  Filter filter,
                                  Set<DispatcherType> dispatcherTypes) {
        this(filterName, pathPattern, null, filter, dispatcherTypes, Map.of());
    }

    public FilterRegistrationSpec(String filterName,
                                  String pathPattern,
                                  Filter filter,
                                  Set<DispatcherType> dispatcherTypes,
                                  Map<String, String> initParameters) {
        this(filterName, pathPattern, null, filter, dispatcherTypes, initParameters);
    }

    public static FilterRegistrationSpec forServletName(String filterName,
                                                        String servletName,
                                                        Filter filter,
                                                        Set<DispatcherType> dispatcherTypes,
                                                        Map<String, String> initParameters) {
        return new FilterRegistrationSpec(filterName, null, servletName, filter, dispatcherTypes, initParameters);
    }

    public boolean appliesToDispatcher(DispatcherType dispatcherType) {
        return dispatcherTypes.contains(dispatcherType);
    }

    public boolean isUrlPatternMapping() {
        return pathPattern != null;
    }

    public boolean isServletNameMapping() {
        return servletName != null;
    }

    public boolean matches(String path, DispatcherType dispatcherType) {
        return matchesPath(path, dispatcherType);
    }

    public boolean matchesPath(String path, DispatcherType dispatcherType) {
        return isUrlPatternMapping()
                && appliesToDispatcher(dispatcherType)
                && matchesPath(pathPattern, path);
    }

    public boolean matchesServlet(String candidateServletName, DispatcherType dispatcherType) {
        if (!isServletNameMapping() || !appliesToDispatcher(dispatcherType) || candidateServletName == null) {
            return false;
        }
        return "*".equals(servletName) || servletName.equals(candidateServletName);
    }

    private static String normalizePattern(String pattern) {
        if (pattern.isEmpty()) {
            return "";
        }
        if (pattern.equals("/") || pattern.equals("/*")) {
            return "/*";
        }
        if (pattern.startsWith("*.")) {
            return pattern;
        }
        return pattern.startsWith("/") ? pattern : "/" + pattern;
    }

    private static boolean matchesPath(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.equals(path)) {
            return true;
        }
        if ("/*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            if (!path.startsWith(prefix)) {
                return false;
            }
            return path.length() == prefix.length() || path.charAt(prefix.length()) == '/';
        }
        if (pattern.startsWith("*.")) {
            int slash = path.lastIndexOf('/');
            int period = path.lastIndexOf('.');
            if (period <= slash || period == path.length() - 1) {
                return false;
            }
            String extension = pattern.substring(2);
            return path.regionMatches(period + 1, extension, 0, extension.length())
                    && path.length() - period - 1 == extension.length();
        }
        if (pattern.isEmpty()) {
            return "/".equals(path);
        }
        return false;
    }
}
