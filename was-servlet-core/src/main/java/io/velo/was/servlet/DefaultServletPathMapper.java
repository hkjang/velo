package io.velo.was.servlet;

import java.util.Set;

final class DefaultServletPathMapper implements ServletPathMapper {

    @Override
    public ServletPathMatch resolve(Set<String> mappings, String applicationRelativePath) {
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }

        String normalizedPath = normalizePath(applicationRelativePath);

        if (mappings.contains(normalizedPath) && !"/".equals(normalizedPath)) {
            return new ServletPathMatch(
                    normalizedPath,
                    normalizedPath,
                    null,
                    ServletPathMatch.MatchType.EXACT);
        }

        String bestPrefixMapping = null;
        int bestPrefixLength = -1;
        for (String mapping : mappings) {
            if (mapping == null || !mapping.endsWith("/*")) {
                continue;
            }
            String prefix = mapping.substring(0, mapping.length() - 2);
            if (!prefix.isEmpty()
                    && !normalizedPath.equals(prefix)
                    && !normalizedPath.startsWith(prefix + "/")) {
                continue;
            }
            if (prefix.length() > bestPrefixLength) {
                bestPrefixMapping = mapping;
                bestPrefixLength = prefix.length();
            }
        }
        if (bestPrefixMapping != null) {
            String prefix = bestPrefixMapping.substring(0, bestPrefixMapping.length() - 2);
            String servletPath = prefix;
            String pathInfo = normalizedPath.equals(prefix)
                    ? null
                    : normalizedPath.substring(prefix.length());
            return new ServletPathMatch(
                    bestPrefixMapping,
                    servletPath,
                    pathInfo,
                    ServletPathMatch.MatchType.PATH_PREFIX);
        }

        String lastSegment = lastSegment(normalizedPath);
        String bestExtensionMapping = null;
        int bestExtensionLength = -1;
        for (String mapping : mappings) {
            if (mapping == null || !mapping.startsWith("*.")) {
                continue;
            }
            String extension = mapping.substring(1);
            if (lastSegment.endsWith(extension) && mapping.length() > bestExtensionLength) {
                bestExtensionMapping = mapping;
                bestExtensionLength = mapping.length();
            }
        }
        if (bestExtensionMapping != null) {
            return new ServletPathMatch(
                    bestExtensionMapping,
                    normalizedPath,
                    null,
                    ServletPathMatch.MatchType.EXTENSION);
        }

        if (mappings.contains("/")) {
            return new ServletPathMatch(
                    "/",
                    normalizedPath,
                    null,
                    ServletPathMatch.MatchType.DEFAULT);
        }

        if (mappings.contains(normalizedPath)) {
            return new ServletPathMatch(
                    normalizedPath,
                    normalizedPath,
                    null,
                    ServletPathMatch.MatchType.DEFAULT);
        }

        return null;
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

    private static String lastSegment(String path) {
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0) {
            return path;
        }
        return path.substring(slashIndex + 1);
    }
}
