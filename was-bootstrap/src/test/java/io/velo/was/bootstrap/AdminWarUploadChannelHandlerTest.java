package io.velo.was.bootstrap;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.servlet.InMemoryHttpSessionStore;
import io.velo.was.servlet.SessionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminWarUploadChannelHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void streamsMultipartWarUploadAndDeploysCustomContext() throws Exception {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getWebAdmin().setContextPath("/admin");
        configuration.getServer().getDeploy().setDirectory(tempDir.resolve("deploy").toString());
        configuration.getServer().getDeploy().setHotDeploy(true);

        InMemoryHttpSessionStore sessionStore = new InMemoryHttpSessionStore();
        SessionState session = sessionStore.create();
        session.attributes().put("velo.admin.authenticated", true);
        session.attributes().put("velo.csrf.token", "csrf-token");

        AtomicReference<Path> deployedWarPath = new AtomicReference<>();
        AtomicReference<String> deployedContextPath = new AtomicReference<>();

        AdminWarUploadService uploadService = new AdminWarUploadService(
                configuration,
                sessionStore,
                (warPath, contextPath) -> {
                    deployedWarPath.set(warPath);
                    deployedContextPath.set(contextPath);
                });

        EmbeddedChannel channel = new EmbeddedChannel(
                new AdminWarUploadChannelHandler(uploadService, tempDir.resolve("staging")));

        byte[] multipartBody = multipartBody(
                "----velo-boundary",
                "/tadpole-upload",
                "ROOT.war",
                "fake-war-binary".getBytes(StandardCharsets.UTF_8));

        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/admin/upload-war");
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=----velo-boundary");
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, multipartBody.length);
        request.headers().set(HttpHeaderNames.COOKIE, "JSESSIONID=" + session.getId());
        request.headers().set("X-CSRF-Token", "csrf-token");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        channel.writeInbound(request);
        channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(multipartBody, 0, 48)));
        channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(multipartBody, 48, 77)));
        channel.writeInbound(new DefaultLastHttpContent(
                Unpooled.wrappedBuffer(multipartBody, 125, multipartBody.length - 125)));

        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        try {
            assertEquals(200, response.status().code());
            String body = response.content().toString(StandardCharsets.UTF_8);
            assertTrue(body.contains("\"success\":true"));
            assertTrue(body.contains("/tadpole-upload"));
        } finally {
            response.release();
        }

        assertEquals("/tadpole-upload", deployedContextPath.get());
        assertNotNull(deployedWarPath.get());
        assertTrue(Files.exists(deployedWarPath.get()));
        assertTrue(deployedWarPath.get().startsWith(tempDir.resolve("deploy").resolve(".uploads")));
        assertEquals("fake-war-binary", Files.readString(deployedWarPath.get(), StandardCharsets.UTF_8));

        channel.finishAndReleaseAll();
    }

    private static byte[] multipartBody(String boundary,
                                        String contextPath,
                                        String fileName,
                                        byte[] fileBytes) {
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"contextPath\"\r\n\r\n"
                + contextPath + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];
        System.arraycopy(prefixBytes, 0, body, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, body, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, body, prefixBytes.length + fileBytes.length, suffixBytes.length);
        return body;
    }
}
