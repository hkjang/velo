package io.velo.was.webadmin.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Serves embedded static resources (CSS, JS, images) for the Web Admin UI.
 * Resources are loaded from the classpath under {@code webadmin/static/}.
 */
public class AdminStaticResourceServlet extends HttpServlet {

    private static final Map<String, String> MIME_TYPES = Map.of(
            ".css", "text/css; charset=UTF-8",
            ".js", "application/javascript; charset=UTF-8",
            ".json", "application/json; charset=UTF-8",
            ".html", "text/html; charset=UTF-8",
            ".png", "image/png",
            ".svg", "image/svg+xml",
            ".ico", "image/x-icon",
            ".woff2", "font/woff2",
            ".woff", "font/woff"
    );

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Prevent directory traversal
        if (pathInfo.contains("..")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String resourcePath = "webadmin/static" + pathInfo;
        InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contentType = resolveContentType(pathInfo);
        resp.setContentType(contentType);
        resp.setHeader("Cache-Control", "public, max-age=3600");

        try (in; OutputStream out = resp.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private static String resolveContentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String ext = path.substring(dot);
            String type = MIME_TYPES.get(ext);
            if (type != null) return type;
        }
        return "application/octet-stream";
    }
}
