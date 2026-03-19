package io.velo.was.servlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Compatibility-oriented mapper that keeps the same precedence as Tomcat's
 * servlet wrapper matching. The vendored upstream reference lives under
 * vendor/upstream/apache-tomcat/11.0.18/java/org/apache/catalina/mapper/Mapper.java.
 */
final class TomcatCompatibleServletPathMapper implements ServletPathMapper {

    private final ConcurrentMap<List<String>, MappingTable> cache = new ConcurrentHashMap<>();

    @Override
    public ServletPathMatch resolve(Set<String> mappings, String applicationRelativePath) {
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }

        String normalizedPath = normalizePath(applicationRelativePath);
        MappingTable table = cache.computeIfAbsent(cacheKey(mappings), MappingTable::fromMappings);

        ServletPathMatch exact = resolveExact(table.exactWrappers, normalizedPath);
        if (exact != null) {
            return exact;
        }

        ServletPathMatch wildcard = resolveWildcard(table, normalizedPath);
        if (wildcard != null) {
            return wildcard;
        }

        ServletPathMatch extension = resolveExtension(table.extensionWrappers, normalizedPath);
        if (extension != null) {
            return extension;
        }

        if (table.defaultWrapper) {
            return new ServletPathMatch(
                    "/",
                    normalizedPath,
                    null,
                    ServletPathMatch.MatchType.DEFAULT);
        }

        return null;
    }

    private static List<String> cacheKey(Set<String> mappings) {
        List<String> key = new ArrayList<>(mappings);
        Collections.sort(key);
        return List.copyOf(key);
    }

    private static ServletPathMatch resolveExact(List<String> exactWrappers, String path) {
        int index = Collections.binarySearch(exactWrappers, path);
        if (index < 0) {
            return null;
        }

        String mapping = exactWrappers.get(index);
        return new ServletPathMatch(
                mapping,
                mapping,
                null,
                ServletPathMatch.MatchType.EXACT);
    }

    private static ServletPathMatch resolveWildcard(MappingTable table, String path) {
        if (table.wildcardWrappers.isEmpty()) {
            return null;
        }

        String candidatePath = path;
        int slashBudget = table.nesting + 1;
        while (candidatePath != null) {
            int floorIndex = findFloor(table.wildcardWrappers, candidatePath);
            if (floorIndex >= 0) {
                String prefix = table.wildcardWrappers.get(floorIndex);
                if (matchesWildcardPrefix(prefix, path)) {
                    String pathInfo = path.length() == prefix.length() ? null : path.substring(prefix.length());
                    return new ServletPathMatch(
                            prefix + "/*",
                            prefix,
                            pathInfo,
                            ServletPathMatch.MatchType.PATH_PREFIX);
                }
            }

            int nextSlash = slashBudget > 0
                    ? nthSlash(candidatePath, slashBudget--)
                    : lastSlash(candidatePath);
            if (nextSlash <= 0) {
                break;
            }
            candidatePath = candidatePath.substring(0, nextSlash);
        }

        return null;
    }

    private static ServletPathMatch resolveExtension(List<String> extensionWrappers, String path) {
        if (extensionWrappers.isEmpty()) {
            return null;
        }

        int slash = path.lastIndexOf('/');
        int period = path.lastIndexOf('.');
        if (period <= slash || period == path.length() - 1) {
            return null;
        }

        String extension = path.substring(period + 1);
        int index = Collections.binarySearch(extensionWrappers, extension);
        if (index < 0) {
            return null;
        }

        return new ServletPathMatch(
                "*." + extensionWrappers.get(index),
                path,
                null,
                ServletPathMatch.MatchType.EXTENSION);
    }

    private static boolean matchesWildcardPrefix(String prefix, String fullPath) {
        if (!fullPath.startsWith(prefix)) {
            return false;
        }
        return fullPath.length() == prefix.length() || fullPath.charAt(prefix.length()) == '/';
    }

    private static int findFloor(List<String> sortedValues, String target) {
        int index = Collections.binarySearch(sortedValues, target);
        return index >= 0 ? index : -index - 2;
    }

    private static int nthSlash(String path, int slashNumber) {
        int slashCount = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                slashCount++;
                if (slashCount == slashNumber) {
                    return i;
                }
            }
        }
        return path.length();
    }

    private static int lastSlash(String path) {
        return path.lastIndexOf('/');
    }

    private static int slashCount(String path) {
        int slashCount = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                slashCount++;
            }
        }
        return slashCount;
    }

    private static String normalizePath(String applicationRelativePath) {
        if (applicationRelativePath == null || applicationRelativePath.isBlank()) {
            return "/";
        }
        if (applicationRelativePath.startsWith("/")) {
            return applicationRelativePath;
        }
        return "/" + applicationRelativePath;
    }

    private record MappingTable(
            List<String> exactWrappers,
            List<String> wildcardWrappers,
            List<String> extensionWrappers,
            boolean defaultWrapper,
            int nesting
    ) {
        private static MappingTable fromMappings(List<String> mappings) {
            List<String> exactWrappers = new ArrayList<>();
            List<String> wildcardWrappers = new ArrayList<>();
            List<String> extensionWrappers = new ArrayList<>();
            boolean defaultWrapper = false;
            int nesting = 0;

            for (String mapping : mappings) {
                if (mapping == null || mapping.isBlank()) {
                    exactWrappers.add("/");
                    continue;
                }
                if ("/".equals(mapping)) {
                    defaultWrapper = true;
                } else if (mapping.endsWith("/*")) {
                    String prefix = mapping.substring(0, mapping.length() - 2);
                    wildcardWrappers.add(prefix);
                    nesting = Math.max(nesting, slashCount(prefix));
                } else if (mapping.startsWith("*.")) {
                    extensionWrappers.add(mapping.substring(2));
                } else {
                    exactWrappers.add(mapping);
                }
            }

            Collections.sort(exactWrappers);
            Collections.sort(wildcardWrappers);
            Collections.sort(extensionWrappers);

            return new MappingTable(
                    List.copyOf(exactWrappers),
                    List.copyOf(wildcardWrappers),
                    List.copyOf(extensionWrappers),
                    defaultWrapper,
                    nesting);
        }
    }
}
