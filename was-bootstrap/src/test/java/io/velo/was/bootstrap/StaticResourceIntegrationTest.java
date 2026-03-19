package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.deploy.WarDeployer;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.transport.netty.NettyServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void servesStaticAssetsWithBrowserFriendlyHeaders() throws Exception {
        Path warDir = tempDir.resolve("static-site");
        Path webInf = warDir.resolve("WEB-INF");
        Files.createDirectories(webInf);

        Files.writeString(webInf.resolve("web.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                    <display-name>static-site</display-name>
                    <context-param>
                        <param-name>io.velo.was.static.spaFallback</param-name>
                        <param-value>/index.html</param-value>
                    </context-param>
                    <context-param>
                        <param-name>io.velo.was.static.sourceMaps.enabled</param-name>
                        <param-value>false</param-value>
                    </context-param>
                    <welcome-file-list>
                        <welcome-file>index.html</welcome-file>
                    </welcome-file-list>
                </web-app>
                """, StandardCharsets.UTF_8);

        Files.writeString(warDir.resolve("index.html"), """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Static Test</title>
                    <script type="module" src="/site/assets/app.mjs"></script>
                </head>
                <body>Static Index</body>
                </html>
                """, StandardCharsets.UTF_8);

        Path assetsDir = warDir.resolve("assets");
        Files.createDirectories(assetsDir);
        Files.writeString(assetsDir.resolve("app.mjs"), "export const ready = true;\n", StandardCharsets.UTF_8);
        Files.writeString(assetsDir.resolve("app.js.map"), "{\"version\":3}", StandardCharsets.UTF_8);
        Files.writeString(assetsDir.resolve("app.1234abcd.js"), "console.log('plain');\n", StandardCharsets.UTF_8);
        byte[] precompressedBytes = "pretend-brotli-payload".getBytes(StandardCharsets.UTF_8);
        Files.write(assetsDir.resolve("app.1234abcd.js.br"), precompressedBytes);

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ServerConfiguration.Server serverConfig = new ServerConfiguration.Server();
        ServerConfiguration.Listener listener = new ServerConfiguration.Listener();
        listener.setHost("127.0.0.1");
        listener.setPort(port);
        serverConfig.setListener(listener);

        WarDeployer warDeployer = new WarDeployer(tempDir.resolve("work"));
        WarDeployer.DeploymentResult deploymentResult = warDeployer.deploy(warDir, "/site");

        SimpleServletContainer servletContainer = new SimpleServletContainer();
        servletContainer.deploy(deploymentResult.application());

        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .fallback(servletContainer::handle);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try (NettyServer server = new NettyServer(serverConfig, registry)) {
            server.start();
            String base = "http://127.0.0.1:" + port;

            HttpResponse<String> rootResponse = send(client, base + "/site/", "text/html", null,
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, rootResponse.statusCode());
            assertTrue(rootResponse.body().contains("Static Index"));
            assertEquals("no-cache", rootResponse.headers().firstValue("Cache-Control").orElse(null));

            HttpResponse<String> moduleResponse = send(client, base + "/site/assets/app.mjs", null, null,
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, moduleResponse.statusCode());
            assertEquals("text/javascript; charset=UTF-8",
                    moduleResponse.headers().firstValue("Content-Type").orElse(null));

            HttpResponse<byte[]> scriptResponse = send(client, base + "/site/assets/app.1234abcd.js", null, "br",
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, scriptResponse.statusCode());
            assertEquals("text/javascript; charset=UTF-8",
                    scriptResponse.headers().firstValue("Content-Type").orElse(null));
            assertEquals("br", scriptResponse.headers().firstValue("Content-Encoding").orElse(null));
            assertEquals("Accept-Encoding", scriptResponse.headers().firstValue("Vary").orElse(null));
            assertTrue(scriptResponse.headers().firstValue("Cache-Control").orElse("").contains("immutable"));
            assertArrayEquals(precompressedBytes, scriptResponse.body());

            String etag = scriptResponse.headers().firstValue("ETag").orElse(null);
            assertNotNull(etag);
            HttpResponse<String> notModifiedResponse = send(client, base + "/site/assets/app.1234abcd.js", null, null,
                    HttpRequest.newBuilder()
                            .header("If-None-Match", etag)
                            .header("Accept-Encoding", "br"),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(304, notModifiedResponse.statusCode());

            HttpResponse<String> sourceMapResponse = send(client, base + "/site/assets/app.js.map", null, null,
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, sourceMapResponse.statusCode());

            HttpResponse<String> spaFallbackResponse = send(client, base + "/site/dashboard/overview", "text/html", null,
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, spaFallbackResponse.statusCode());
            assertTrue(spaFallbackResponse.body().contains("Static Index"));
        } finally {
            warDeployer.cleanup(deploymentResult);
        }
    }

    @Test
    void injectsRapCompatibilityPatchForRootContextWelcomePage() throws Exception {
        Path warDir = tempDir.resolve("ROOT");
        Path webInf = warDir.resolve("WEB-INF");
        Files.createDirectories(webInf);

        Files.writeString(webInf.resolve("web.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                    <display-name>root-rap-app</display-name>
                    <welcome-file-list>
                        <welcome-file>index.html</welcome-file>
                    </welcome-file-list>
                </web-app>
                """, StandardCharsets.UTF_8);

        Files.writeString(warDir.resolve("index.html"), """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>RAP Root Test</title>
                    <script type="text/javascript" src="/rwt-resources/440/rap-client.js"></script>
                </head>
                <body>
                    <script type="text/javascript">
                      rwt.remote.MessageProcessor.processMessage({});
                    </script>
                </body>
                </html>
                """, StandardCharsets.UTF_8);

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ServerConfiguration.Server serverConfig = new ServerConfiguration.Server();
        ServerConfiguration.Listener listener = new ServerConfiguration.Listener();
        listener.setHost("127.0.0.1");
        listener.setPort(port);
        serverConfig.setListener(listener);

        WarDeployer warDeployer = new WarDeployer(tempDir.resolve("work"));
        WarDeployer.DeploymentResult deploymentResult = warDeployer.deploy(warDir, "");

        SimpleServletContainer servletContainer = new SimpleServletContainer();
        servletContainer.deploy(deploymentResult.application());

        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .fallback(servletContainer::handle);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try (NettyServer server = new NettyServer(serverConfig, registry)) {
            server.start();
            String base = "http://127.0.0.1:" + port;

            HttpResponse<String> rootResponse = send(client, base + "/", "text/html", null,
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, rootResponse.statusCode());
            assertTrue(rootResponse.body().contains("__veloRapBrowserCompatPatched"));
            assertTrue(rootResponse.body().contains("var retryWindowMs = 10000;"));
            assertTrue(rootResponse.body().indexOf("__veloRapBrowserCompatPatched")
                    > rootResponse.body().indexOf("rap-client.js"));
        } finally {
            warDeployer.cleanup(deploymentResult);
        }
    }

    private static <T> HttpResponse<T> send(HttpClient client,
                                            String url,
                                            String accept,
                                            String acceptEncoding,
                                            HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        return send(client, url, accept, acceptEncoding, HttpRequest.newBuilder(), bodyHandler);
    }

    private static <T> HttpResponse<T> send(HttpClient client,
                                            String url,
                                            String accept,
                                            String acceptEncoding,
                                            HttpRequest.Builder requestBuilder,
                                            HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        requestBuilder.uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
        if (accept != null) {
            requestBuilder.header("Accept", accept);
        }
        if (acceptEncoding != null) {
            requestBuilder.header("Accept-Encoding", acceptEncoding);
        }
        return client.send(requestBuilder.build(), bodyHandler);
    }
}
