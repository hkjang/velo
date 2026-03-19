package io.velo.was.servlet;

record ServletPathMatch(
        String mapping,
        String servletPath,
        String pathInfo,
        MatchType matchType
) {
    enum MatchType {
        EXACT,
        PATH_PREFIX,
        EXTENSION,
        DEFAULT
    }
}
