package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.transport.netty.NettyServer;
import io.velo.was.webadmin.WebAdminApplication;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAdminLoginIntegrationTest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Test
    void loginWizardPasswordChangePersistsAcrossLoginAndApi() throws Exception {
        int port = findFreePort();
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getListener().setHost("127.0.0.1");
        configuration.getServer().getListener().setPort(port);
        configuration.getServer().getWebAdmin().setEnabled(true);
        configuration.getServer().getWebAdmin().setContextPath("/admin");

        SimpleServletContainer container = new SimpleServletContainer();
        container.deploy(WebAdminApplication.create(configuration));

        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .fallback(container::handle);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        try (NettyServer server = new NettyServer(configuration.getServer(), registry)) {
            server.start();
            String base = "http://127.0.0.1:" + port;

            HttpResponse<String> loginPage = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(base + "/admin/login"))
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, loginPage.statusCode());

            String initialCookie = cookieHeader(loginPage);
            String initialCsrf = extractCsrf(loginPage.body());

            HttpResponse<String> passwordChange = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(base + "/admin/login?action=change-default-password"))
                            .header("Cookie", initialCookie)
                            .header("Content-Type", "application/json")
                            .header("X-CSRF-Token", initialCsrf)
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"username":"admin","currentPassword":"admin","password":"changed-admin-1"}
                                    """.trim()))
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, passwordChange.statusCode());
            assertTrue(passwordChange.body().contains("\"success\":true"), passwordChange.body());

            HttpResponse<String> loginWithNewPassword = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(base + "/admin/login"))
                            .header("Cookie", initialCookie)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(formData(
                                    "username", "admin",
                                    "password", "changed-admin-1",
                                    "_csrf", initialCsrf)))
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(302, loginWithNewPassword.statusCode());
            assertEquals("/admin/", loginWithNewPassword.headers().firstValue("Location").orElseThrow());

            HttpResponse<String> secondLoginPage = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(base + "/admin/login"))
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, secondLoginPage.statusCode());

            String secondCookie = cookieHeader(secondLoginPage);
            String secondCsrf = extractCsrf(secondLoginPage.body());

            HttpResponse<String> loginWithOldPassword = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(base + "/admin/login"))
                            .header("Cookie", secondCookie)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(formData(
                                    "username", "admin",
                                    "password", "admin",
                                    "_csrf", secondCsrf)))
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(302, loginWithOldPassword.statusCode());
            assertEquals("/admin/login?error=1", loginWithOldPassword.headers().firstValue("Location").orElseThrow());
        } finally {
            container.close();
        }
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String cookieHeader(HttpResponse<?> response) {
        return response.headers().firstValue("Set-Cookie")
                .or(() -> response.headers().firstValue("set-cookie"))
                .orElseThrow()
                .split(";", 2)[0];
    }

    private static String extractCsrf(String body) {
        Matcher matcher = CSRF_PATTERN.matcher(body);
        assertTrue(matcher.find(), "Expected CSRF token in login page");
        return matcher.group(1);
    }

    private static String formData(String... keyValues) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(keyValues[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(keyValues[i + 1], StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
