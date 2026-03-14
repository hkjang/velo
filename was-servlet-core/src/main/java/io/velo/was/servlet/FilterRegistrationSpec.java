package io.velo.was.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;

import java.util.EnumSet;
import java.util.Set;

public record FilterRegistrationSpec(
        String pathPattern,
        Filter filter,
        Set<DispatcherType> dispatcherTypes
) {
    public FilterRegistrationSpec {
        if (pathPattern == null || pathPattern.isBlank()) {
            pathPattern = "/*";
        }
        pathPattern = normalizePattern(pathPattern);
        dispatcherTypes = dispatcherTypes == null || dispatcherTypes.isEmpty()
                ? EnumSet.allOf(DispatcherType.class)
                : EnumSet.copyOf(dispatcherTypes);
    }

    public boolean matches(String path, DispatcherType dispatcherType) {
        return dispatcherTypes.contains(dispatcherType) && matchesPath(pathPattern, path);
    }

    private static String normalizePattern(String pattern) {
        if (pattern.equals("/") || pattern.equals("/*")) {
            return "/*";
        }
        return pattern.startsWith("/") ? pattern : "/" + pattern;
    }

    private static boolean matchesPath(String pattern, String path) {
        if ("/*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return pattern.equals(path);
    }
}
