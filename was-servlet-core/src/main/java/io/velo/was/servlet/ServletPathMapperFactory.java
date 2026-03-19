package io.velo.was.servlet;

public final class ServletPathMapperFactory {

    private ServletPathMapperFactory() {
    }

    public static ServletPathMapper fromStrategy(String strategy) {
        String normalized = strategy == null ? "TOMCAT_COMPAT" : strategy.trim().toUpperCase();
        return switch (normalized) {
            case "VELO" -> new DefaultServletPathMapper();
            case "TOMCAT_COMPAT" -> new TomcatCompatibleServletPathMapper();
            default -> throw new IllegalArgumentException("Unsupported servlet mapping strategy: " + strategy);
        };
    }
}
