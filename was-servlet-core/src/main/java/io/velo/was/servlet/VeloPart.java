package io.velo.was.servlet;

import jakarta.servlet.http.Part;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple in-memory implementation of {@link Part} for multipart/form-data uploads.
 */
final class VeloPart implements Part {

    private final String name;
    private final String submittedFileName;
    private final byte[] body;
    private final Map<String, List<String>> headers;

    VeloPart(String name, String submittedFileName, byte[] body, Map<String, List<String>> headers) {
        this.name = name;
        this.submittedFileName = submittedFileName;
        this.body = body;
        this.headers = new LinkedHashMap<>();
        headers.forEach((key, values) -> this.headers.put(key.toLowerCase(), values));
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(body);
    }

    @Override
    public String getContentType() {
        String ct = getHeader("content-type");
        return ct != null ? ct : "application/octet-stream";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSubmittedFileName() {
        return submittedFileName;
    }

    @Override
    public long getSize() {
        return body.length;
    }

    @Override
    public void write(String fileName) throws IOException {
        Files.write(Path.of(fileName), body);
    }

    @Override
    public void delete() {
        // In-memory; nothing to clean up
    }

    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values != null && !values.isEmpty() ? values.getFirst() : null;
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values != null ? values : Collections.emptyList();
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }
}
