package io.velo.was.servlet;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses multipart/form-data request bodies into {@link VeloPart} instances.
 */
final class MultipartParser {

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=([^;\\s]+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]+)\"");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]*)\"");

    private MultipartParser() {
    }

    /**
     * Parses multipart/form-data content.
     *
     * @param body        raw request body bytes
     * @param contentType Content-Type header value (must contain boundary)
     * @return parsed parts, or empty list if not multipart
     */
    static List<VeloPart> parse(byte[] body, String contentType) {
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            return List.of();
        }

        Matcher boundaryMatcher = BOUNDARY_PATTERN.matcher(contentType);
        if (!boundaryMatcher.find()) {
            return List.of();
        }

        String boundary = boundaryMatcher.group(1);
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        byte[] finalBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);

        List<VeloPart> parts = new ArrayList<>();
        List<byte[]> sections = splitByBoundary(body, boundaryBytes);

        for (byte[] section : sections) {
            if (section.length == 0 || startsWith(section, finalBoundaryBytes)) {
                continue;
            }

            int headerEnd = findHeaderEnd(section);
            if (headerEnd < 0) {
                continue;
            }

            String headerBlock = new String(section, 0, headerEnd, StandardCharsets.UTF_8);
            byte[] partBody = Arrays.copyOfRange(section, headerEnd + 4, section.length); // skip \r\n\r\n

            // Trim trailing \r\n from part body
            if (partBody.length >= 2
                    && partBody[partBody.length - 2] == '\r'
                    && partBody[partBody.length - 1] == '\n') {
                partBody = Arrays.copyOf(partBody, partBody.length - 2);
            }

            Map<String, List<String>> headers = parseHeaders(headerBlock);
            String disposition = headers.getOrDefault("content-disposition", List.of("")).getFirst();

            Matcher nameMatcher = NAME_PATTERN.matcher(disposition);
            String name = nameMatcher.find() ? nameMatcher.group(1) : "unknown";

            Matcher filenameMatcher = FILENAME_PATTERN.matcher(disposition);
            String filename = filenameMatcher.find() ? filenameMatcher.group(1) : null;

            parts.add(new VeloPart(name, filename, partBody, headers));
        }

        return parts;
    }

    private static List<byte[]> splitByBoundary(byte[] body, byte[] boundary) {
        List<byte[]> sections = new ArrayList<>();
        int start = indexOf(body, boundary, 0);
        if (start < 0) {
            return sections;
        }
        start += boundary.length;
        // Skip past \r\n after first boundary
        if (start < body.length - 1 && body[start] == '\r' && body[start + 1] == '\n') {
            start += 2;
        }

        while (start < body.length) {
            int next = indexOf(body, boundary, start);
            if (next < 0) {
                // Remaining bytes
                if (start < body.length) {
                    sections.add(Arrays.copyOfRange(body, start, body.length));
                }
                break;
            }
            sections.add(Arrays.copyOfRange(body, start, next));
            start = next + boundary.length;
            // Skip past \r\n after boundary
            if (start < body.length - 1 && body[start] == '\r' && body[start + 1] == '\n') {
                start += 2;
            }
        }
        return sections;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        outer:
        for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int findHeaderEnd(byte[] section) {
        for (int i = 0; i < section.length - 3; i++) {
            if (section[i] == '\r' && section[i + 1] == '\n'
                    && section[i + 2] == '\r' && section[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, List<String>> parseHeaders(String headerBlock) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String line : headerBlock.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
        return headers;
    }
}
