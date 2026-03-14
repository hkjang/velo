package io.velo.was.deploy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a WAR (Web Application Archive) file into a target directory.
 */
public final class WarExtractor {

    private static final Logger log = LoggerFactory.getLogger(WarExtractor.class);

    private WarExtractor() {
    }

    /**
     * Extracts a WAR file into the specified target directory.
     *
     * @param warFile   the WAR file path
     * @param targetDir the directory to extract into
     * @return the extraction target directory (same as targetDir)
     * @throws IOException if extraction fails
     */
    public static Path extract(Path warFile, Path targetDir) throws IOException {
        if (!Files.exists(warFile)) {
            throw new IOException("WAR file does not exist: " + warFile);
        }
        if (!warFile.getFileName().toString().endsWith(".war")) {
            throw new IOException("Not a WAR file: " + warFile);
        }

        Files.createDirectories(targetDir);

        try (InputStream fileIn = Files.newInputStream(warFile);
             ZipInputStream zipIn = new ZipInputStream(fileIn)) {

            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path entryTarget = targetDir.resolve(entry.getName()).normalize();

                // Zip slip protection
                if (!entryTarget.startsWith(targetDir)) {
                    throw new IOException("Zip slip detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryTarget);
                } else {
                    Files.createDirectories(entryTarget.getParent());
                    Files.copy(zipIn, entryTarget, StandardCopyOption.REPLACE_EXISTING);
                }
                entryCount++;
                zipIn.closeEntry();
            }

            log.info("Extracted WAR {} to {} ({} entries)", warFile.getFileName(), targetDir, entryCount);
        }
        return targetDir;
    }

    /**
     * Checks if the directory looks like an exploded WAR (has WEB-INF/web.xml or WEB-INF/ directory).
     */
    public static boolean isExplodedWar(Path directory) {
        return Files.isDirectory(directory.resolve("WEB-INF"));
    }
}
