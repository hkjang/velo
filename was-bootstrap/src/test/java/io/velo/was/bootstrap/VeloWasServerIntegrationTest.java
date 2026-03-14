package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.HttpResponses;
import io.velo.was.servlet.SimpleServletApplication;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.transport.netty.NettyServer;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeloWasServerIntegrationTest {

    @Test
    void servesHealthAndServletEndpointsOverNetty() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ServerConfiguration.Server serverConfiguration = new ServerConfiguration.Server();
        ServerConfiguration.Listener listener = new ServerConfiguration.Listener();
        listener.setHost("127.0.0.1");
        listener.setPort(port);
        serverConfiguration.setListener(listener);

        SimpleServletContainer servletContainer = new SimpleServletContainer();
        SampleLifecycleListener lifecycleListener = new SampleLifecycleListener();
        servletContainer.deploy(SimpleServletApplication.builder("sample-app", "/app")
                .filter(new SampleTraceFilter())
                .servletContextListener(lifecycleListener)
                .servletRequestListener(lifecycleListener)
                .servlet("/hello", new SampleHelloServlet())
                .build());

        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .registerGet("/health", exchange -> HttpResponses.jsonOk("""
                        {"status":"UP","name":"integration-node","nodeId":"node-it"}
                        """.trim()))
                .fallback(servletContainer::handle);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try (NettyServer server = new NettyServer(serverConfiguration, registry)) {
            server.start();

            HttpResponse<String> health = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"UP\""));

            HttpResponse<String> firstHello = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/app/hello"))
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstHello.statusCode());
            assertTrue(firstHello.body().contains("\"visits\":1"));
            assertTrue(firstHello.body().contains("\"requestCount\":1"));
            assertTrue(firstHello.body().contains("\"lifecycle\":\"started\""));
            assertEquals("applied", firstHello.headers().firstValue("x-velo-filter").orElseThrow());

            String cookie = firstHello.headers().firstValue("set-cookie").orElseThrow();
            HttpResponse<String> secondHello = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/app/hello"))
                            .header("Cookie", cookie.split(";", 2)[0])
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, secondHello.statusCode());
            assertTrue(secondHello.body().contains("\"visits\":2"));
            assertTrue(secondHello.body().contains("\"requestCount\":2"));
        }
    }
}
