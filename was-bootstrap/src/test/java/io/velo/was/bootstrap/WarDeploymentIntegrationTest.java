package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.deploy.WarDeployer;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.HttpResponses;
import io.velo.was.jsp.JspServlet;
import io.velo.was.servlet.SimpleServletApplication;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.transport.netty.NettyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: WAR deployment with servlet and JSP.
 */
class WarDeploymentIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void deployWarAndServeServletAndJsp() throws Exception {
        // --- 1. Create exploded WAR ---
        Path warDir = tempDir.resolve("test-war");
        Path webInf = warDir.resolve("WEB-INF");
        Files.createDirectories(webInf);

        // web.xml referencing TestGreetingServlet (already on test classpath)
        Files.writeString(webInf.resolve("web.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                    <display-name>test-war</display-name>
                    <servlet>
                        <servlet-name>greeting</servlet-name>
                        <servlet-class>io.velo.was.bootstrap.TestGreetingServlet</servlet-class>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>greeting</servlet-name>
                        <url-pattern>/greet</url-pattern>
                    </servlet-mapping>
                </web-app>
                """);

        // JSP pages
        Files.writeString(warDir.resolve("index.jsp"), """
                <%@ page contentType="text/html; charset=UTF-8" %>
                <html><body>
                <h1>JSP Works!</h1>
                <p>Java: <%= System.getProperty("java.version") %></p>
                </body></html>
                """);

        Files.writeString(warDir.resolve("hello.jsp"), """
                <%@ page contentType="text/html; charset=UTF-8" %>
                <% request.setAttribute("user", "Velo"); %>
                <html><body>
                <h1>Hello <%= request.getAttribute("user") %></h1>
                </body></html>
                """);

        // --- 2. Allocate random port ---
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // --- 3. Configure and deploy ---
        ServerConfiguration.Server serverConfig = new ServerConfiguration.Server();
        ServerConfiguration.Listener listener = new ServerConfiguration.Listener();
        listener.setHost("127.0.0.1");
        listener.setPort(port);
        serverConfig.setListener(listener);

        // Deploy WAR (GreetingServlet loaded from test classloader)
        WarDeployer warDeployer = new WarDeployer(tempDir.resolve("work"));
        WarDeployer.DeploymentResult deploymentResult = warDeployer.deploy(warDir, "/testapp");

        SimpleServletContainer servletContainer = new SimpleServletContainer();
        servletContainer.deploy(deploymentResult.application());

        // Deploy JSP-enabled app with webAppRoot set to the WAR directory
        Path jspScratchDir = tempDir.resolve("jsp-work");
        Files.createDirectories(jspScratchDir);

        SimpleServletApplication jspApp = SimpleServletApplication.builder("jsp-app", "/jsp")
                .servlet("*.jsp", new JspServlet())
                .initParameter("io.velo.was.jsp.webAppRoot", warDir.toString())
                .initParameter("io.velo.was.jsp.scratchDir", jspScratchDir.toString())
                .build();
        servletContainer.deploy(jspApp);

        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .registerGet("/health", exchange -> HttpResponses.jsonOk("""
                        {"status":"UP"}
                        """.trim()))
                .fallback(servletContainer::handle);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // --- 4. Start server, run tests ---
        try (NettyServer server = new NettyServer(serverConfig, registry)) {
            server.start();
            String base = "http://127.0.0.1:" + port;

            // Test health
            HttpResponse<String> healthResp = get(client, base + "/health");
            assertEquals(200, healthResp.statusCode());
            assertTrue(healthResp.body().contains("\"status\":\"UP\""));

            // Test WAR servlet
            HttpResponse<String> greetResp = get(client, base + "/testapp/greet?name=World");
            assertEquals(200, greetResp.statusCode());
            assertTrue(greetResp.body().contains("Hello, World!"), "body: " + greetResp.body());

            // Test JSP page
            HttpResponse<String> jspResp = get(client, base + "/jsp/index.jsp");
            assertEquals(200, jspResp.statusCode());
            assertTrue(jspResp.body().contains("JSP Works!"), "JSP body: " + jspResp.body());

            // Test JSP with scriptlet and expression
            HttpResponse<String> helloResp = get(client, base + "/jsp/hello.jsp");
            assertEquals(200, helloResp.statusCode());
            assertTrue(helloResp.body().contains("Hello Velo"), "hello body: " + helloResp.body());

            System.out.println("=== WAR + JSP Integration Tests Passed ===");
            System.out.println("Greeting: " + greetResp.body().trim());
            System.out.println("JSP index: " + jspResp.body().trim());
            System.out.println("JSP hello: " + helloResp.body().trim());
        } finally {
            warDeployer.cleanup(deploymentResult);
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
