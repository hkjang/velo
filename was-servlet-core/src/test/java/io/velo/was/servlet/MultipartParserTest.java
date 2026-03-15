package io.velo.was.servlet;

import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultipartParserTest {

    @Test
    void parseSingleTextPart() {
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        String body = """
                ------WebKitFormBoundary7MA4YWxkTrZu0gW\r
                Content-Disposition: form-data; name="field1"\r
                \r
                value1\r
                ------WebKitFormBoundary7MA4YWxkTrZu0gW--\r
                """;

        List<VeloPart> parts = MultipartParser.parse(
                body.getBytes(StandardCharsets.UTF_8),
                "multipart/form-data; boundary=" + boundary);

        assertEquals(1, parts.size());
        VeloPart part = parts.getFirst();
        assertEquals("field1", part.getName());
        assertNull(part.getSubmittedFileName());
        assertEquals("value1", new String(readAll(part), StandardCharsets.UTF_8));
    }

    @Test
    void parseMultipleParts() {
        String boundary = "boundary123";
        String body = """
                --boundary123\r
                Content-Disposition: form-data; name="name"\r
                \r
                John\r
                --boundary123\r
                Content-Disposition: form-data; name="age"\r
                \r
                30\r
                --boundary123--\r
                """;

        List<VeloPart> parts = MultipartParser.parse(
                body.getBytes(StandardCharsets.UTF_8),
                "multipart/form-data; boundary=boundary123");

        assertEquals(2, parts.size());
        assertEquals("name", parts.get(0).getName());
        assertEquals("John", new String(readAll(parts.get(0)), StandardCharsets.UTF_8));
        assertEquals("age", parts.get(1).getName());
        assertEquals("30", new String(readAll(parts.get(1)), StandardCharsets.UTF_8));
    }

    @Test
    void parseFileUpload() {
        String boundary = "boundary456";
        String body = """
                --boundary456\r
                Content-Disposition: form-data; name="file"; filename="test.txt"\r
                Content-Type: text/plain\r
                \r
                file content here\r
                --boundary456--\r
                """;

        List<VeloPart> parts = MultipartParser.parse(
                body.getBytes(StandardCharsets.UTF_8),
                "multipart/form-data; boundary=boundary456");

        assertEquals(1, parts.size());
        VeloPart part = parts.getFirst();
        assertEquals("file", part.getName());
        assertEquals("test.txt", part.getSubmittedFileName());
        assertEquals("text/plain", part.getContentType());
        assertEquals("file content here", new String(readAll(part), StandardCharsets.UTF_8));
    }

    @Test
    void parseNonMultipartReturnsEmpty() {
        List<VeloPart> parts = MultipartParser.parse(
                "hello".getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertTrue(parts.isEmpty());
    }

    @Test
    void parseNullContentTypeReturnsEmpty() {
        List<VeloPart> parts = MultipartParser.parse(
                "hello".getBytes(StandardCharsets.UTF_8), null);
        assertTrue(parts.isEmpty());
    }

    @Test
    void partHeadersAreCaseInsensitive() {
        String boundary = "b";
        String body = "--b\r\nContent-Disposition: form-data; name=\"f\"\r\nContent-Type: text/html\r\n\r\ndata\r\n--b--\r\n";

        List<VeloPart> parts = MultipartParser.parse(
                body.getBytes(StandardCharsets.UTF_8),
                "multipart/form-data; boundary=b");

        assertEquals(1, parts.size());
        assertEquals("text/html", parts.getFirst().getHeader("Content-Type"));
        assertEquals("text/html", parts.getFirst().getHeader("content-type"));
        assertEquals("text/html", parts.getFirst().getHeader("CONTENT-TYPE"));
    }

    @Test
    void partImplementsAllMethods() throws IOException {
        String boundary = "b";
        String body = "--b\r\nContent-Disposition: form-data; name=\"f\"; filename=\"a.bin\"\r\nContent-Type: application/octet-stream\r\n\r\nABC\r\n--b--\r\n";

        List<VeloPart> parts = MultipartParser.parse(
                body.getBytes(StandardCharsets.UTF_8),
                "multipart/form-data; boundary=b");

        Part part = parts.getFirst();
        assertEquals("f", part.getName());
        assertEquals("a.bin", part.getSubmittedFileName());
        assertEquals(3, part.getSize());
        assertEquals("application/octet-stream", part.getContentType());

        Collection<String> headerNames = part.getHeaderNames();
        assertTrue(headerNames.contains("content-disposition"));
        assertTrue(headerNames.contains("content-type"));

        // getInputStream should return part body
        byte[] fromStream = part.getInputStream().readAllBytes();
        assertEquals("ABC", new String(fromStream, StandardCharsets.UTF_8));

        // delete should not throw
        part.delete();
    }

    private static byte[] readAll(Part part) {
        try {
            return part.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
