package io.velo.was.deploy;

import io.velo.was.servlet.ServletApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class WarDeployerTest {

    @TempDir
    Path tempDir;

    @Test
    void deploysExplodedWarWithWebXml() throws Exception {
        Path appRoot = createExplodedWar();
        WarDeployer deployer = new WarDeployer(tempDir.resolve("work"));

        WarDeployer.DeploymentResult result = deployer.deploy(appRoot, "/myapp");

        assertNotNull(result);
        ServletApplication app = result.application();
        assertEquals("test-app", app.name());
        assertEquals("/myapp", app.contextPath());
        assertFalse(result.extracted());
        assertEquals(1, app.servlets().size());
        assertTrue(app.servlets().containsKey("/hello"));
        assertEquals(1, app.filters().size());
        assertEquals("world", app.initParameters().get("greeting"));
        assertEquals(1, app.servletContextListeners().size());
        assertEquals(1, app.servletContextAttributeListeners().size());
        assertEquals(1, app.httpSessionListeners().size());
        assertEquals(1, app.httpSessionAttributeListeners().size());
        assertEquals(1, app.httpSessionIdListeners().size());
    }

    @Test
    void deploysWarFileWithExtraction() throws Exception {
        Path warFile = createWarFile();
        Path workDir = tempDir.resolve("work");
        WarDeployer deployer = new WarDeployer(workDir);

        WarDeployer.DeploymentResult result = deployer.deploy(warFile, "/extracted");

        assertNotNull(result);
        assertTrue(result.extracted());
        assertEquals("/extracted", result.application().contextPath());
        assertTrue(Files.isDirectory(result.appRoot()));
    }

    @Test
    void parsesWebXmlDescriptorCorrectly() throws Exception {
        Path appRoot = createExplodedWar();
        Path webXml = appRoot.resolve("WEB-INF").resolve("web.xml");

        WebXmlDescriptor descriptor = WebXmlParser.parse(webXml);

        assertEquals("test-app", descriptor.displayName());
        assertEquals(1, descriptor.servlets().size());
        assertEquals("HelloServlet", descriptor.servlets().get(0).name());
        assertEquals(1, descriptor.servletMappings().size());
        assertEquals("/hello", descriptor.servletMappings().get(0).urlPattern());
        assertEquals(1, descriptor.filters().size());
        assertEquals("TestFilter", descriptor.filters().get(0).name());
        assertEquals(1, descriptor.filterMappings().size());
        assertEquals(1, descriptor.listenerClasses().size());
        assertEquals(1, descriptor.contextParams().size());
        assertEquals("world", descriptor.contextParams().get("greeting"));
    }

    @Test
    void emptyDescriptorWhenNoWebXml() throws Exception {
        WebXmlDescriptor descriptor = WebXmlParser.parse(tempDir.resolve("nonexistent.xml"));

        assertNotNull(descriptor);
        assertTrue(descriptor.servlets().isEmpty());
        assertTrue(descriptor.filters().isEmpty());
    }

    @Test
    void warExtractorDetectsExplodedWar() throws Exception {
        Path appRoot = createExplodedWar();
        assertTrue(WarExtractor.isExplodedWar(appRoot));
        assertFalse(WarExtractor.isExplodedWar(tempDir));
    }

    @Test
    void rejectsNonWarFile() {
        Path notWar = tempDir.resolve("app.zip");
        WarDeployer deployer = new WarDeployer(tempDir.resolve("work"));
        assertThrows(DeploymentException.class, () -> deployer.deploy(notWar, "/"));
    }

    @Test
    void cleanupRemovesExtractedDirectory() throws Exception {
        Path warFile = createWarFile();
        Path workDir = tempDir.resolve("work");
        WarDeployer deployer = new WarDeployer(workDir);

        WarDeployer.DeploymentResult result = deployer.deploy(warFile, "/cleanup-test");
        assertTrue(Files.isDirectory(result.appRoot()));

        deployer.cleanup(result);
        assertFalse(Files.exists(result.appRoot()));
    }

    private Path createExplodedWar() throws Exception {
        Path appRoot = tempDir.resolve("test-app");
        Path webInf = appRoot.resolve("WEB-INF");
        Path classes = webInf.resolve("classes");
        Files.createDirectories(classes);

        // Create a simple servlet class
        String servletSource = """
                package test;
                import jakarta.servlet.http.*;
                import java.io.*;
                public class HelloServlet extends HttpServlet {
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        resp.getWriter().write("Hello");
                    }
                }
                """;
        compileAndPlaceClass(classes, "test.HelloServlet", servletSource);

        // Create a simple filter class
        String filterSource = """
                package test;
                import jakarta.servlet.*;
                import java.io.*;
                public class TestFilter implements Filter {
                    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
                        chain.doFilter(req, res);
                    }
                }
                """;
        compileAndPlaceClass(classes, "test.TestFilter", filterSource);

        // Create a simple listener class
        String listenerSource = """
                package test;
                import jakarta.servlet.*;
                import jakarta.servlet.http.*;
                public class TestListener implements ServletContextListener, ServletContextAttributeListener,
                        HttpSessionListener, HttpSessionAttributeListener, HttpSessionIdListener {
                    public void contextInitialized(ServletContextEvent sce) {}
                    public void contextDestroyed(ServletContextEvent sce) {}
                    public void attributeAdded(ServletContextAttributeEvent event) {}
                    public void attributeRemoved(ServletContextAttributeEvent event) {}
                    public void attributeReplaced(ServletContextAttributeEvent event) {}
                    public void sessionCreated(HttpSessionEvent se) {}
                    public void sessionDestroyed(HttpSessionEvent se) {}
                    public void attributeAdded(HttpSessionBindingEvent event) {}
                    public void attributeRemoved(HttpSessionBindingEvent event) {}
                    public void attributeReplaced(HttpSessionBindingEvent event) {}
                    public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {}
                }
                """;
        compileAndPlaceClass(classes, "test.TestListener", listenerSource);

        // Create web.xml
        String webXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                    <display-name>test-app</display-name>
                    <context-param>
                        <param-name>greeting</param-name>
                        <param-value>world</param-value>
                    </context-param>
                    <servlet>
                        <servlet-name>HelloServlet</servlet-name>
                        <servlet-class>test.HelloServlet</servlet-class>
                        <load-on-startup>1</load-on-startup>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>HelloServlet</servlet-name>
                        <url-pattern>/hello</url-pattern>
                    </servlet-mapping>
                    <filter>
                        <filter-name>TestFilter</filter-name>
                        <filter-class>test.TestFilter</filter-class>
                    </filter>
                    <filter-mapping>
                        <filter-name>TestFilter</filter-name>
                        <url-pattern>/*</url-pattern>
                        <dispatcher>REQUEST</dispatcher>
                    </filter-mapping>
                    <listener>
                        <listener-class>test.TestListener</listener-class>
                    </listener>
                </web-app>
                """;
        Files.writeString(webInf.resolve("web.xml"), webXml, StandardCharsets.UTF_8);

        return appRoot;
    }

    private Path createWarFile() throws Exception {
        Path exploded = createExplodedWar();
        Path warFile = tempDir.resolve("test-app.war");

        try (OutputStream out = Files.newOutputStream(warFile);
             JarOutputStream jar = new JarOutputStream(out)) {
            addToJar(jar, exploded, exploded);
        }

        return warFile;
    }

    private void addToJar(JarOutputStream jar, Path base, Path source) throws Exception {
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    String entryName = base.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path)) {
                        if (!entryName.isEmpty()) {
                            jar.putNextEntry(new JarEntry(entryName + "/"));
                            jar.closeEntry();
                        }
                    } else {
                        jar.putNextEntry(new JarEntry(entryName));
                        Files.copy(path, jar);
                        jar.closeEntry();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void compileAndPlaceClass(Path classesDir, String className, String source) throws Exception {
        // Use runtime compilation via javax.tools
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available. Run tests with JDK, not JRE.");
        }

        Path srcDir = tempDir.resolve("src-" + className.replace('.', '-'));
        String relativePath = className.replace('.', '/') + ".java";
        Path srcFile = srcDir.resolve(relativePath);
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, source, StandardCharsets.UTF_8);

        // Find jakarta.servlet-api jar on the classpath
        String classpath = System.getProperty("java.class.path");

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = compiler.run(null, null, errors, "-cp", classpath, "-d", classesDir.toString(), srcFile.toString());
        if (result != 0) {
            throw new RuntimeException("Compilation failed for " + className + ": " + errors.toString(StandardCharsets.UTF_8));
        }
    }
}
