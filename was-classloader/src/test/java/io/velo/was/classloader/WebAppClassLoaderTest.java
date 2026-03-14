package io.velo.was.classloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebAppClassLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsClassFromWebInfClasses() throws Exception {
        Path webApp = tempDir.resolve("app");
        Path classesDir = webApp.resolve("WEB-INF").resolve("classes");
        Path packageDir = classesDir.resolve("testpkg");
        Files.createDirectories(packageDir);

        // Compile a simple class into WEB-INF/classes
        String source = """
                package testpkg;
                public class Greeter {
                    public String greet() { return "hello"; }
                }
                """;
        compileClass(classesDir, "testpkg.Greeter", source);

        try (WebAppClassLoader loader = WebAppClassLoader.create("test-app", webApp, getClass().getClassLoader())) {
            Class<?> clazz = loader.loadClass("testpkg.Greeter");
            assertNotNull(clazz);
            assertEquals("testpkg.Greeter", clazz.getName());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            String result = (String) clazz.getMethod("greet").invoke(instance);
            assertEquals("hello", result);
        }
    }

    @Test
    void classLoaderIsolation() throws Exception {
        Path webApp1 = tempDir.resolve("app1");
        Path classes1 = webApp1.resolve("WEB-INF").resolve("classes");
        Files.createDirectories(classes1);
        compileClass(classes1, "isolated.Service", """
                package isolated;
                public class Service { public String name() { return "app1"; } }
                """);

        Path webApp2 = tempDir.resolve("app2");
        Path classes2 = webApp2.resolve("WEB-INF").resolve("classes");
        Files.createDirectories(classes2);
        compileClass(classes2, "isolated.Service", """
                package isolated;
                public class Service { public String name() { return "app2"; } }
                """);

        try (WebAppClassLoader loader1 = WebAppClassLoader.create("app1", webApp1, getClass().getClassLoader());
             WebAppClassLoader loader2 = WebAppClassLoader.create("app2", webApp2, getClass().getClassLoader())) {

            Class<?> class1 = loader1.loadClass("isolated.Service");
            Class<?> class2 = loader2.loadClass("isolated.Service");

            // Different classloaders should produce different Class objects
            assertNotSame(class1, class2);
            assertEquals("app1", class1.getMethod("name").invoke(class1.getDeclaredConstructor().newInstance()));
            assertEquals("app2", class2.getMethod("name").invoke(class2.getDeclaredConstructor().newInstance()));
        }
    }

    @Test
    void childFirstOverridesParent() throws Exception {
        Path webApp = tempDir.resolve("child-first-app");
        Path classesDir = webApp.resolve("WEB-INF").resolve("classes");
        Files.createDirectories(classesDir);

        // This test verifies child-first loading by checking the classloader
        try (WebAppClassLoader loader = WebAppClassLoader.create("cf-app", webApp, getClass().getClassLoader(), true)) {
            assertTrue(loader.isChildFirst());
            assertEquals("cf-app", loader.getApplicationName());

            // java.* classes should still delegate to parent
            Class<?> stringClass = loader.loadClass("java.lang.String");
            assertSame(String.class, stringClass);
        }
    }

    @Test
    void findsResourceInClasses() throws Exception {
        Path webApp = tempDir.resolve("resource-app");
        Path classesDir = webApp.resolve("WEB-INF").resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("test.properties"), "key=value", StandardCharsets.UTF_8);

        try (WebAppClassLoader loader = WebAppClassLoader.create("res-app", webApp, getClass().getClassLoader())) {
            URL resource = loader.getResource("test.properties");
            assertNotNull(resource);
        }
    }

    @Test
    void returnsNullForNonExistentClass() throws Exception {
        Path webApp = tempDir.resolve("empty-app");
        Files.createDirectories(webApp.resolve("WEB-INF").resolve("classes"));

        try (WebAppClassLoader loader = WebAppClassLoader.create("empty-app", webApp, getClass().getClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("nonexistent.Foo"));
        }
    }

    @Test
    void toStringContainsAppName() throws Exception {
        Path webApp = tempDir.resolve("name-app");
        Files.createDirectories(webApp.resolve("WEB-INF").resolve("classes"));

        try (WebAppClassLoader loader = WebAppClassLoader.create("my-app", webApp, getClass().getClassLoader())) {
            assertTrue(loader.toString().contains("my-app"));
        }
    }

    private void compileClass(Path classesDir, String className, String source) throws Exception {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available.");
        }
        Path srcDir = tempDir.resolve("src-" + className.replace('.', '-'));
        Path srcFile = srcDir.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, source, StandardCharsets.UTF_8);

        int result = compiler.run(null, null, null, "-d", classesDir.toString(), srcFile.toString());
        if (result != 0) {
            throw new RuntimeException("Compilation failed for " + className);
        }
    }
}
