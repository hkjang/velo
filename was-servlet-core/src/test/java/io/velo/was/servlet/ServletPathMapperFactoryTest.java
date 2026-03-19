package io.velo.was.servlet;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServletPathMapperFactoryTest {

    @Test
    void createsTomcatCompatibleMapperByDefault() {
        ServletPathMapper mapper = ServletPathMapperFactory.fromStrategy(null);

        assertInstanceOf(TomcatCompatibleServletPathMapper.class, mapper);
    }

    @Test
    void createsVeloMapperWhenRequested() {
        ServletPathMapper mapper = ServletPathMapperFactory.fromStrategy("velo");

        assertInstanceOf(DefaultServletPathMapper.class, mapper);
    }

    @Test
    void tomcatCompatibleMapperUsesServletPrecedence() {
        ServletPathMapper mapper = ServletPathMapperFactory.fromStrategy("TOMCAT_COMPAT");
        Set<String> mappings = new LinkedHashSet<>();
        mappings.add("/catalog");
        mappings.add("/catalog/*");
        mappings.add("/");

        ServletPathMatch exact = mapper.resolve(mappings, "/catalog");
        ServletPathMatch nested = mapper.resolve(mappings, "/catalog/items");

        assertEquals("/catalog", exact.mapping());
        assertEquals(ServletPathMatch.MatchType.EXACT, exact.matchType());
        assertEquals("/catalog/*", nested.mapping());
        assertEquals("/catalog", nested.servletPath());
        assertEquals("/items", nested.pathInfo());
    }

    @Test
    void tomcatCompatibleMapperBacktracksWildcardPrefixesBySlashBoundary() {
        ServletPathMapper mapper = ServletPathMapperFactory.fromStrategy("TOMCAT_COMPAT");
        Set<String> mappings = new LinkedHashSet<>();
        mappings.add("/files/public/*");
        mappings.add("/files/*");
        mappings.add("/");

        ServletPathMatch nested = mapper.resolve(mappings, "/files/private/archive.txt");

        assertEquals("/files/*", nested.mapping());
        assertEquals(ServletPathMatch.MatchType.PATH_PREFIX, nested.matchType());
        assertEquals("/files", nested.servletPath());
        assertEquals("/private/archive.txt", nested.pathInfo());
    }

    @Test
    void tomcatCompatibleMapperResolvesExtensionBeforeDefault() {
        ServletPathMapper mapper = ServletPathMapperFactory.fromStrategy("TOMCAT_COMPAT");
        Set<String> mappings = new LinkedHashSet<>();
        mappings.add("*.jsp");
        mappings.add("/");

        ServletPathMatch match = mapper.resolve(mappings, "/pages/home.jsp");

        assertEquals("*.jsp", match.mapping());
        assertEquals(ServletPathMatch.MatchType.EXTENSION, match.matchType());
        assertEquals("/pages/home.jsp", match.servletPath());
    }

    @Test
    void rejectsUnknownStrategy() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class,
                        () -> ServletPathMapperFactory.fromStrategy("other"));

        assertEquals("Unsupported servlet mapping strategy: other", error.getMessage());
    }
}
