package io.velo.was.jsp;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.jsp.compiler.CompiledJsp;
import io.velo.was.jsp.compiler.JspCompilationException;
import io.velo.was.jsp.compiler.JspCompiler;
import io.velo.was.jsp.parser.JspDocument;
import io.velo.was.jsp.parser.JspParser;
import io.velo.was.jsp.reload.JspReloadManager;
import io.velo.was.jsp.resource.JspResourceManager;
import io.velo.was.jsp.translator.JspTranslator;
import io.velo.was.jsp.translator.TranslatedSource;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet that handles JSP requests by parsing, translating, compiling, and executing JSP files.
 * <p>
 * This servlet is registered with url-pattern {@code *.jsp} and {@code *.jspx}.
 * On each request:
 * <ol>
 *     <li>Resolve the JSP file path from the request URI</li>
 *     <li>Check cache / detect changes (in development mode)</li>
 *     <li>Parse → Translate → Compile (if needed)</li>
 *     <li>Load the generated servlet class and invoke {@code service()}</li>
 * </ol>
 */
public class JspServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(JspServlet.class);

    private JspParser parser;
    private JspTranslator translator;
    private JspCompiler compiler;
    private JspResourceManager resourceManager;
    private JspReloadManager reloadManager;
    private final Map<String, LoadedJsp> loadedJsps = new ConcurrentHashMap<>();
    private ServerConfiguration.Jsp jspConfig;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.parser = new JspParser();
        this.translator = new JspTranslator();

        // Use defaults if no config is provided via context
        this.jspConfig = new ServerConfiguration.Jsp();
        Object configAttr = config.getServletContext().getAttribute("io.velo.was.jsp.config");
        if (configAttr instanceof ServerConfiguration.Jsp jc) {
            this.jspConfig = jc;
        }

        Path scratchDir = Path.of(jspConfig.getScratchDir());
        try {
            Files.createDirectories(scratchDir);
        } catch (IOException e) {
            throw new ServletException("Failed to create JSP scratch directory: " + scratchDir, e);
        }

        this.compiler = new JspCompiler(scratchDir);

        // Determine web app root
        String realPath = config.getServletContext().getRealPath("/");
        if (realPath == null) {
            realPath = config.getInitParameter("io.velo.was.jsp.webAppRoot");
        }
        if (realPath == null) {
            realPath = config.getServletContext().getInitParameter("io.velo.was.jsp.webAppRoot");
        }
        Path webAppRoot = realPath != null ? Path.of(realPath) : Path.of(".");
        this.resourceManager = new JspResourceManager(webAppRoot);
        this.reloadManager = new JspReloadManager(jspConfig.isDevelopmentMode());

        log.info("JspServlet initialized: scratchDir={} developmentMode={} precompile={}",
                scratchDir, jspConfig.isDevelopmentMode(), jspConfig.isPrecompile());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String jspPath = resolveJspPath(request);
        if (jspPath == null || !resourceManager.exists(jspPath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "JSP not found: " + jspPath);
            return;
        }

        try {
            Servlet jspServlet = getOrCompile(jspPath);
            jspServlet.service(request, response);
        } catch (JspCompilationException e) {
            log.error("JSP compilation failed: {}", jspPath, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "JSP compilation error: " + e.getMessage());
        } catch (Exception e) {
            log.error("JSP execution failed: {}", jspPath, e);
            throw new ServletException("JSP execution failed: " + jspPath, e);
        }
    }

    private Servlet getOrCompile(String jspPath) throws Exception {
        LoadedJsp loaded = loadedJsps.get(jspPath);

        if (loaded != null) {
            // Check if recompilation is needed
            long currentModified = resourceManager.lastModified(jspPath);
            if (!reloadManager.needsRecompile(jspPath, currentModified)) {
                return loaded.servlet;
            }
            // Invalidate for recompilation
            log.info("Recompiling modified JSP: {}", jspPath);
            resourceManager.invalidateSource(jspPath);
            compiler.invalidate(loaded.className);
        }

        // Parse
        String source = resourceManager.readJspSource(jspPath);
        JspDocument document = parser.parse(source, jspPath);

        // Translate
        TranslatedSource translated = translator.translate(document);

        // Compile
        CompiledJsp compiled = compiler.compile(translated, getClass().getClassLoader());

        // Load class
        JspClassLoader classLoader = new JspClassLoader(compiled.allClassBytes(), getClass().getClassLoader());
        Class<?> servletClass = classLoader.loadClass(compiled.className());
        Servlet servlet = (Servlet) servletClass.getDeclaredConstructor().newInstance();
        servlet.init(getServletConfig());

        // Track
        long lastModified = resourceManager.lastModified(jspPath);
        reloadManager.recordModificationTime(jspPath, lastModified);
        loadedJsps.put(jspPath, new LoadedJsp(compiled.className(), servlet));

        return servlet;
    }

    private String resolveJspPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        if (pathInfo != null) {
            return servletPath + pathInfo;
        }
        return servletPath;
    }

    @Override
    public void destroy() {
        for (LoadedJsp loaded : loadedJsps.values()) {
            loaded.servlet.destroy();
        }
        loadedJsps.clear();
        compiler.clearCache();
        resourceManager.clearCache();
        reloadManager.clear();
    }

    // --- Inner classes ---

    private record LoadedJsp(String className, Servlet servlet) {}

    /**
     * ClassLoader that loads compiled JSP classes from byte arrays.
     */
    private static class JspClassLoader extends ClassLoader {
        private final Map<String, byte[]> classBytes;

        JspClassLoader(Map<String, byte[]> classBytes, ClassLoader parent) {
            super(parent);
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
        }
    }
}
