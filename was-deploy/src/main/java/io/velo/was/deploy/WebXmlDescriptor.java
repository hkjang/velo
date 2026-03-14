package io.velo.was.deploy;

import java.util.List;
import java.util.Map;

/**
 * Immutable representation of a parsed WEB-INF/web.xml deployment descriptor.
 */
public record WebXmlDescriptor(
        String displayName,
        Map<String, String> contextParams,
        List<ServletDef> servlets,
        List<ServletMapping> servletMappings,
        List<FilterDef> filters,
        List<FilterMapping> filterMappings,
        List<String> listenerClasses,
        List<String> welcomeFiles
) {
    public static WebXmlDescriptor empty() {
        return new WebXmlDescriptor(null, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Finds the servlet class name for a given url-pattern.
     */
    public String resolveServletClass(String urlPattern) {
        for (ServletMapping mapping : servletMappings) {
            if (mapping.urlPattern.equals(urlPattern)) {
                for (ServletDef servlet : servlets) {
                    if (servlet.name.equals(mapping.servletName)) {
                        return servlet.className;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the servlet definition by name.
     */
    public ServletDef servletByName(String name) {
        return servlets.stream().filter(s -> s.name.equals(name)).findFirst().orElse(null);
    }

    /**
     * Returns the filter definition by name.
     */
    public FilterDef filterByName(String name) {
        return filters.stream().filter(f -> f.name.equals(name)).findFirst().orElse(null);
    }

    public record ServletDef(
            String name,
            String className,
            Map<String, String> initParams,
            int loadOnStartup,
            boolean asyncSupported
    ) {
    }

    public record ServletMapping(
            String servletName,
            String urlPattern
    ) {
    }

    public record FilterDef(
            String name,
            String className,
            Map<String, String> initParams,
            boolean asyncSupported
    ) {
    }

    public record FilterMapping(
            String filterName,
            String urlPattern,
            List<String> dispatchers
    ) {
    }
}
