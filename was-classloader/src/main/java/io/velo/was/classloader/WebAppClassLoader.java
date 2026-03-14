package io.velo.was.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A ClassLoader that loads classes from a web application's WEB-INF directory.
 * <p>
 * Follows the servlet spec convention:
 * <ul>
 *     <li>Classes from WEB-INF/classes/ are loaded first</li>
 *     <li>JARs from WEB-INF/lib/*.jar are loaded next</li>
 * </ul>
 * <p>
 * Supports parent-first (default, standard Java delegation) or child-first
 * (webapp classes override server classes) loading strategies.
 */
public class WebAppClassLoader extends URLClassLoader {

    private static final Logger log = LoggerFactory.getLogger(WebAppClassLoader.class);

    private final String applicationName;
    private final boolean childFirst;

    private WebAppClassLoader(String applicationName, URL[] urls, ClassLoader parent, boolean childFirst) {
        super(urls, parent);
        this.applicationName = applicationName;
        this.childFirst = childFirst;
    }

    /**
     * Creates a WebAppClassLoader for the given exploded WAR directory.
     *
     * @param applicationName the application name for logging
     * @param webAppRoot      the root of the exploded WAR (the directory containing WEB-INF)
     * @param parent          the parent ClassLoader
     * @param childFirst      if true, classes from the webapp take precedence over parent
     * @return a new WebAppClassLoader
     * @throws IOException if the directory cannot be scanned
     */
    public static WebAppClassLoader create(String applicationName,
                                           Path webAppRoot,
                                           ClassLoader parent,
                                           boolean childFirst) throws IOException {
        List<URL> urls = new ArrayList<>();

        Path classesDir = webAppRoot.resolve("WEB-INF").resolve("classes");
        if (Files.isDirectory(classesDir)) {
            urls.add(classesDir.toUri().toURL());
            log.debug("app={} added classes directory: {}", applicationName, classesDir);
        }

        Path libDir = webAppRoot.resolve("WEB-INF").resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(libDir, "*.jar")) {
                for (Path jar : jars) {
                    urls.add(jar.toUri().toURL());
                    log.debug("app={} added lib jar: {}", applicationName, jar.getFileName());
                }
            }
        }

        log.info("app={} classloader created with {} URLs, childFirst={}", applicationName, urls.size(), childFirst);
        return new WebAppClassLoader(applicationName, urls.toArray(URL[]::new), parent, childFirst);
    }

    /**
     * Creates a WebAppClassLoader with parent-first delegation (default).
     */
    public static WebAppClassLoader create(String applicationName,
                                           Path webAppRoot,
                                           ClassLoader parent) throws IOException {
        return create(applicationName, webAppRoot, parent, false);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }

            if (!childFirst) {
                return super.loadClass(name, resolve);
            }

            // Child-first: try this classloader's URLs first, then delegate to parent
            // Always delegate java.* and jakarta.servlet.* to parent to avoid LinkageError
            if (name.startsWith("java.") || name.startsWith("jakarta.servlet.")) {
                return super.loadClass(name, resolve);
            }

            try {
                Class<?> found = findClass(name);
                if (resolve) {
                    resolveClass(found);
                }
                return found;
            } catch (ClassNotFoundException e) {
                return super.loadClass(name, resolve);
            }
        }
    }

    @Override
    public URL getResource(String name) {
        if (!childFirst) {
            return super.getResource(name);
        }
        URL url = findResource(name);
        if (url != null) {
            return url;
        }
        return super.getResource(name);
    }

    public String getApplicationName() {
        return applicationName;
    }

    public boolean isChildFirst() {
        return childFirst;
    }

    @Override
    public String toString() {
        return "WebAppClassLoader[" + applicationName + "]";
    }
}
