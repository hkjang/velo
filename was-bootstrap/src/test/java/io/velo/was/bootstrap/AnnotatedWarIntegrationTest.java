package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.deploy.WarDeployer;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.transport.netty.NettyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotatedWarIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void deploysAndServesAnnotationOnlyWar() throws Exception {
        Path warDir = tempDir.resolve("annotated-war");
        Path classesDir = warDir.resolve("WEB-INF").resolve("classes");
        Files.createDirectories(classesDir);

        compileAndPlaceClass(classesDir, "test.AnnotationHelloServlet", """
                package test;
                import jakarta.servlet.annotation.WebServlet;
                import jakarta.servlet.http.HttpServlet;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import java.io.IOException;
                @WebServlet(name = "AnnotationHelloServlet", urlPatterns = "/hello")
                public class AnnotationHelloServlet extends HttpServlet {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                        Object marker = req.getServletContext().getAttribute("listenerMarker");
                        resp.getWriter().write("HELLO:" + marker);
                    }
                }
                """);

        compileAndPlaceClass(classesDir, "test.AnnotationHeaderFilter", """
                package test;
                import jakarta.servlet.*;
                import jakarta.servlet.annotation.WebFilter;
                import jakarta.servlet.annotation.WebInitParam;
                import jakarta.servlet.http.HttpServletResponse;
                import java.io.IOException;
                @WebFilter(filterName = "AnnotationHeaderFilter", servletNames = "AnnotationHelloServlet",
                        initParams = @WebInitParam(name = "headerValue", value = "annotated"))
                public class AnnotationHeaderFilter implements Filter {
                    private String headerValue;
                    @Override
                    public void init(FilterConfig filterConfig) {
                        this.headerValue = filterConfig.getInitParameter("headerValue");
                    }
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        ((HttpServletResponse) response).setHeader("X-Annotation-Filter", headerValue);
                        chain.doFilter(request, response);
                    }
                }
                """);

        compileAndPlaceClass(classesDir, "test.AnnotationLifecycleListener", """
                package test;
                import jakarta.servlet.ServletContextEvent;
                import jakarta.servlet.ServletContextListener;
                import jakarta.servlet.annotation.WebListener;
                @WebListener
                public class AnnotationLifecycleListener implements ServletContextListener {
                    @Override
                    public void contextInitialized(ServletContextEvent sce) {
                        sce.getServletContext().setAttribute("listenerMarker", "ready");
                    }
                }
                """);

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ServerConfiguration.Server serverConfig = new ServerConfiguration.Server();
        ServerConfiguration.Listener listener = new ServerConfiguration.Listener();
        listener.setHost("127.0.0.1");
        listener.setPort(port);
        serverConfig.setListener(listener);

        WarDeployer deployer = new WarDeployer(tempDir.resolve("work"));
        WarDeployer.DeploymentResult deploymentResult = deployer.deploy(warDir, "/ann");
        SimpleServletContainer servletContainer = new SimpleServletContainer();
        servletContainer.deploy(deploymentResult.application());

        HttpHandlerRegistry registry = new HttpHandlerRegistry().fallback(servletContainer::handle);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try (NettyServer server = new NettyServer(serverConfig, registry)) {
            server.start();

            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/ann/hello"))
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("HELLO:ready", response.body());
            assertEquals("annotated", response.headers().firstValue("X-Annotation-Filter").orElse(null));
        } finally {
            deployer.cleanup(deploymentResult);
        }
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
