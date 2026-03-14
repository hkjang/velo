package io.velo.was.jsp.compiler;

import io.velo.was.jsp.translator.TranslatedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles translated JSP Java source code into bytecode using the JDK in-memory compiler.
 */
public class JspCompiler {

    private static final Logger log = LoggerFactory.getLogger(JspCompiler.class);

    private final Path scratchDir;
    private final Map<String, CompiledJsp> cache = new ConcurrentHashMap<>();

    public JspCompiler(Path scratchDir) {
        this.scratchDir = scratchDir;
    }

    public CompiledJsp compile(TranslatedSource source, ClassLoader parentClassLoader)
            throws JspCompilationException {
        String key = source.fullyQualifiedClassName();
        CompiledJsp cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        log.debug("Compiling JSP: {} -> {}", source.sourceJspPath(), key);

        // Write Java source to scratch dir for debugging
        try {
            Path sourceDir = scratchDir.resolve(source.packageName().replace('.', File.separatorChar));
            Files.createDirectories(sourceDir);
            Path sourceFile = sourceDir.resolve(source.className() + ".java");
            Files.writeString(sourceFile, source.javaSource(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to write JSP source to scratch dir: {}", e.getMessage());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new JspCompilationException("No Java compiler available (JDK required)", null);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryFileManager fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8));

        JavaFileObject sourceObject = new InMemoryJavaSource(
                source.fullyQualifiedClassName(), source.javaSource());

        List<String> options = List.of(
                "--release", "21"
        );

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, List.of(sourceObject));

        boolean success = task.call();

        if (!success) {
            StringBuilder errorMsg = new StringBuilder("JSP compilation failed for ")
                    .append(source.sourceJspPath()).append(":\n");
            long errorLine = -1, errorColumn = -1;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errorMsg.append("  ").append(d.getMessage(null)).append("\n");
                    if (errorLine == -1) {
                        errorLine = d.getLineNumber();
                        errorColumn = d.getColumnNumber();
                    }
                }
            }
            throw new JspCompilationException(errorMsg.toString(),
                    source.sourceJspPath(), errorLine, errorColumn);
        }

        Map<String, byte[]> classBytes = fileManager.getClassBytes();
        byte[] mainClassBytes = classBytes.get(source.fullyQualifiedClassName());
        if (mainClassBytes == null && !classBytes.isEmpty()) {
            mainClassBytes = classBytes.values().iterator().next();
        }

        // Write class files to scratch dir
        for (Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            try {
                Path classFile = scratchDir.resolve(
                        entry.getKey().replace('.', File.separatorChar) + ".class");
                Files.createDirectories(classFile.getParent());
                Files.write(classFile, entry.getValue());
            } catch (IOException e) {
                log.warn("Failed to write class file: {}", e.getMessage());
            }
        }

        CompiledJsp compiled = new CompiledJsp(
                source.fullyQualifiedClassName(),
                mainClassBytes,
                classBytes,
                Instant.now());

        cache.put(key, compiled);
        log.info("JSP compiled: {} -> {} ({} bytes)", source.sourceJspPath(), key,
                mainClassBytes != null ? mainClassBytes.length : 0);
        return compiled;
    }

    public void invalidate(String fullyQualifiedClassName) {
        cache.remove(fullyQualifiedClassName);
    }

    public void clearCache() {
        cache.clear();
    }

    public Map<String, CompiledJsp> cachedEntries() {
        return Collections.unmodifiableMap(cache);
    }

    // --- In-memory compilation helpers ---

    private static class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static class InMemoryClassOutput extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        InMemoryClassOutput(String className) {
            super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension),
                    Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, InMemoryClassOutput> outputs = new LinkedHashMap<>();

        InMemoryFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, FileObject sibling) {
            InMemoryClassOutput output = new InMemoryClassOutput(className);
            outputs.put(className, output);
            return output;
        }

        Map<String, byte[]> getClassBytes() {
            Map<String, byte[]> result = new LinkedHashMap<>();
            for (Map.Entry<String, InMemoryClassOutput> entry : outputs.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getBytes());
            }
            return result;
        }
    }
}
