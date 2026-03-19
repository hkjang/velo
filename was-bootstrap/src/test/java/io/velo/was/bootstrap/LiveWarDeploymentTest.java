package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.deploy.WarDeployer;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.HttpResponses;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.transport.netty.NettyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full integration test:
 *   1. Start a real Netty server
 *   2. Deploy the test-war as an exploded WAR
 *   3. Send HTTP requests and verify responses
 *   4. Verify session persistence, filter headers, listener lifecycle
 */
class LiveWarDeploymentTest {

    @TempDir
    Path tempDir;

    private static int findFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void fullIntegrationTest() throws Exception {
        int port = findFreePort();

        // ── 1. Setup server ──
        ServerConfiguration.Server serverConfig = new ServerConfiguration.Server();
        ServerConfiguration.Listener listener = new ServerConfiguration.Listener();
        listener.setHost("127.0.0.1");
        listener.setPort(port);
        serverConfig.setListener(listener);

        SimpleServletContainer container = new SimpleServletContainer();

        // ── 2. Deploy test-war ──
        Path testWarPath = createExplodedTestWar();
        System.out.println("Test WAR path: " + testWarPath);
        assertTrue(testWarPath.resolve("WEB-INF/web.xml").toFile().exists(),
                "test-war/WEB-INF/web.xml should exist at: " + testWarPath);

        WarDeployer deployer = new WarDeployer(tempDir.resolve("work"));
        WarDeployer.DeploymentResult deployResult = deployer.deploy(testWarPath, "/testapp");
        container.deploy(deployResult.application());

        // ── 3. Start server ──
        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .registerGet("/health", exchange -> HttpResponses.jsonOk("{\"status\":\"UP\"}"))
                .fallback(container::handle);

        NettyServer server = new NettyServer(serverConfig, registry);
        server.start();

        String base = "http://127.0.0.1:" + port;
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try {
            // ── 4. Test: Health endpoint ──
            System.out.println("\n=== Test: /health ===");
            HttpResponse<String> healthResp = get(client, base + "/health");
            assertEquals(200, healthResp.statusCode());
            assertTrue(healthResp.body().contains("UP"));
            System.out.println("  Status: " + healthResp.statusCode());
            System.out.println("  Body:   " + healthResp.body());

            // ── 5. Test: HelloServlet ──
            System.out.println("\n=== Test: /testapp/hello ===");
            HttpResponse<String> helloResp = get(client, base + "/testapp/hello?name=Velo");
            assertEquals(200, helloResp.statusCode());
            assertTrue(helloResp.body().contains("Hello, Velo!"));
            assertTrue(helloResp.body().contains("\"contextPath\":\"/testapp\""));
            // Verify TimingFilter header
            assertNotNull(helloResp.headers().firstValue("X-Processing-Time-Us").orElse(null),
                    "TimingFilter should add X-Processing-Time-Us header");
            System.out.println("  Status: " + helloResp.statusCode());
            System.out.println("  Body:   " + helloResp.body());
            System.out.println("  X-Processing-Time-Us: " + helloResp.headers().firstValue("X-Processing-Time-Us").orElse("N/A"));

            // ── 6. Test: HelloServlet with default name ──
            System.out.println("\n=== Test: /testapp/hello (no param) ===");
            HttpResponse<String> helloDefault = get(client, base + "/testapp/hello");
            assertEquals(200, helloDefault.statusCode());
            assertTrue(helloDefault.body().contains("Hello, World!"));
            System.out.println("  Body:   " + helloDefault.body());

            // ── 7. Test: InfoServlet ──
            System.out.println("\n=== Test: /testapp/info ===");
            HttpResponse<String> infoResp = get(client, base + "/testapp/info");
            assertEquals(200, infoResp.statusCode());
            assertTrue(infoResp.body().contains("\"appVersion\":\"1.0.0\""), "Should have appVersion from context-param");
            assertTrue(infoResp.body().contains("velo-was"), "Should have velo-was server info");
            assertTrue(infoResp.body().contains("\"startedAt\":"), "Listener should set startedAt");
            System.out.println("  Status: " + infoResp.statusCode());
            System.out.println("  Body:   " + infoResp.body());

            // ── 8. Test: Session creation ──
            System.out.println("\n=== Test: /testapp/session (first request) ===");
            HttpResponse<String> sess1 = get(client, base + "/testapp/session");
            assertEquals(200, sess1.statusCode());
            assertTrue(sess1.body().contains("\"count\":1"), "First visit should have count=1");
            assertTrue(sess1.body().contains("\"isNew\":true"), "First visit should be new session");
            // Extract JSESSIONID
            String setCookie = sess1.headers().firstValue("Set-Cookie").orElse("");
            assertTrue(setCookie.contains("JSESSIONID"), "Should set JSESSIONID cookie");
            String sessionCookie = setCookie.split(";")[0]; // "JSESSIONID=xxx"
            System.out.println("  Body:    " + sess1.body());
            System.out.println("  Cookie:  " + sessionCookie);

            // ── 9. Test: Session persistence ──
            System.out.println("\n=== Test: /testapp/session (second request, same session) ===");
            HttpResponse<String> sess2 = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(base + "/testapp/session"))
                            .header("Cookie", sessionCookie)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, sess2.statusCode());
            assertTrue(sess2.body().contains("\"count\":2"), "Second visit should have count=2");
            assertTrue(sess2.body().contains("\"isNew\":false"), "Second visit should NOT be new");
            System.out.println("  Body:    " + sess2.body());

            // ── 10. Test: Third request to session ──
            System.out.println("\n=== Test: /testapp/session (third request) ===");
            HttpResponse<String> sess3 = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(base + "/testapp/session"))
                            .header("Cookie", sessionCookie)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, sess3.statusCode());
            assertTrue(sess3.body().contains("\"count\":3"), "Third visit should have count=3");
            System.out.println("  Body:    " + sess3.body());

            // ── 11. Test: 404 for unknown path ──
            System.out.println("\n=== Test: /testapp/unknown (404) ===");
            HttpResponse<String> notFound = get(client, base + "/testapp/unknown");
            assertEquals(404, notFound.statusCode());
            System.out.println("  Status: " + notFound.statusCode());

            // ── 12. Test: 404 for wrong context ──
            System.out.println("\n=== Test: /wrongcontext (404) ===");
            HttpResponse<String> wrongCtx = get(client, base + "/wrongcontext");
            assertEquals(404, wrongCtx.statusCode());
            System.out.println("  Status: " + wrongCtx.statusCode());

            System.out.println("\n=== ALL INTEGRATION TESTS PASSED ===");

        } finally {
            server.close();
            container.close();
        }
    }

    private HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Path createExplodedTestWar() throws Exception {
        Path warRoot = tempDir.resolve("test-war");
        Path webInf = warRoot.resolve("WEB-INF");
        Path classesDir = webInf.resolve("classes");
        Files.createDirectories(classesDir);

        Files.writeString(webInf.resolve("web.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.1">
                    <display-name>velo-test-war</display-name>
                    <context-param>
                        <param-name>appVersion</param-name>
                        <param-value>1.0.0</param-value>
                    </context-param>
                    <listener>
                        <listener-class>io.velo.testwar.AppLifecycleListener</listener-class>
                    </listener>
                    <filter>
                        <filter-name>timingFilter</filter-name>
                        <filter-class>io.velo.testwar.TimingFilter</filter-class>
                    </filter>
                    <filter-mapping>
                        <filter-name>timingFilter</filter-name>
                        <url-pattern>/*</url-pattern>
                    </filter-mapping>
                    <servlet>
                        <servlet-name>helloServlet</servlet-name>
                        <servlet-class>io.velo.testwar.HelloServlet</servlet-class>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>helloServlet</servlet-name>
                        <url-pattern>/hello</url-pattern>
                    </servlet-mapping>
                    <servlet>
                        <servlet-name>sessionServlet</servlet-name>
                        <servlet-class>io.velo.testwar.SessionServlet</servlet-class>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>sessionServlet</servlet-name>
                        <url-pattern>/session</url-pattern>
                    </servlet-mapping>
                    <servlet>
                        <servlet-name>infoServlet</servlet-name>
                        <servlet-class>io.velo.testwar.InfoServlet</servlet-class>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>infoServlet</servlet-name>
                        <url-pattern>/info</url-pattern>
                    </servlet-mapping>
                </web-app>
                """, StandardCharsets.UTF_8);

        compileAndPlaceClass(classesDir, "io.velo.testwar.AppLifecycleListener", """
                package io.velo.testwar;
                import jakarta.servlet.ServletContextEvent;
                import jakarta.servlet.ServletContextListener;
                public class AppLifecycleListener implements ServletContextListener {
                    @Override
                    public void contextInitialized(ServletContextEvent sce) {
                        sce.getServletContext().setAttribute("startedAt", String.valueOf(System.currentTimeMillis()));
                    }
                }
                """);

        compileAndPlaceClass(classesDir, "io.velo.testwar.TimingFilter", """
                package io.velo.testwar;
                import jakarta.servlet.Filter;
                import jakarta.servlet.FilterChain;
                import jakarta.servlet.ServletException;
                import jakarta.servlet.ServletRequest;
                import jakarta.servlet.ServletResponse;
                import jakarta.servlet.http.HttpServletResponse;
                import java.io.IOException;
                public class TimingFilter implements Filter {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        long started = System.nanoTime();
                        chain.doFilter(request, response);
                        long micros = Math.max(1L, (System.nanoTime() - started) / 1000L);
                        ((HttpServletResponse) response).setHeader("X-Processing-Time-Us", String.valueOf(micros));
                    }
                }
                """);

        compileAndPlaceClass(classesDir, "io.velo.testwar.HelloServlet", """
                package io.velo.testwar;
                import jakarta.servlet.http.HttpServlet;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import java.io.IOException;
                public class HelloServlet extends HttpServlet {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        String name = req.getParameter("name");
                        if (name == null || name.isBlank()) {
                            name = "World";
                        }
                        resp.setContentType("application/json; charset=UTF-8");
                        resp.getWriter().write("{\\"message\\":\\"Hello, " + name + "!\\",\\"contextPath\\":\\""
                                + req.getContextPath() + "\\"}");
                    }
                }
                """);

        compileAndPlaceClass(classesDir, "io.velo.testwar.SessionServlet", """
                package io.velo.testwar;
                import jakarta.servlet.http.HttpServlet;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import jakarta.servlet.http.HttpSession;
                import java.io.IOException;
                public class SessionServlet extends HttpServlet {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        HttpSession session = req.getSession(true);
                        boolean isNew = session.isNew();
                        Integer count = (Integer) session.getAttribute("count");
                        int next = count == null ? 1 : count + 1;
                        session.setAttribute("count", next);
                        resp.setContentType("application/json; charset=UTF-8");
                        resp.getWriter().write("{\\"count\\":" + next + ",\\"isNew\\":" + isNew + "}");
                    }
                }
                """);

        compileAndPlaceClass(classesDir, "io.velo.testwar.InfoServlet", """
                package io.velo.testwar;
                import jakarta.servlet.http.HttpServlet;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import java.io.IOException;
                public class InfoServlet extends HttpServlet {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        String appVersion = req.getServletContext().getInitParameter("appVersion");
                        Object startedAt = req.getServletContext().getAttribute("startedAt");
                        resp.setContentType("application/json; charset=UTF-8");
                        resp.getWriter().write("{\\"appVersion\\":\\"" + appVersion + "\\",\\"serverInfo\\":\\""
                                + req.getServletContext().getServerInfo() + "\\",\\"startedAt\\":\\""
                                + String.valueOf(startedAt) + "\\"}");
                    }
                }
                """);

        return warRoot;
    }

    private void compileAndPlaceClass(Path classesDir, String className, String source) throws Exception {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available. Run tests with JDK, not JRE.");
        }

        Path srcDir = tempDir.resolve("src-" + className.replace('.', '-'));
        Path srcFile = srcDir.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, source, StandardCharsets.UTF_8);

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = compiler.run(null, null, errors,
                "-cp", System.getProperty("java.class.path"),
                "-d", classesDir.toString(),
                srcFile.toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed for " + className + ": "
                    + errors.toString(StandardCharsets.UTF_8));
        }
    }
}
