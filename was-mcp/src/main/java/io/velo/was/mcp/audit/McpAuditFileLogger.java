package io.velo.was.mcp.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-based MCP audit logger that writes JSON-line entries to daily-rotating files.
 *
 * <p>Output format: one JSON object per line (JSON Lines / NDJSON), ideal for log aggregation
 * tools (Fluentd, Filebeat, Promtail, etc.).
 *
 * <h3>File naming</h3>
 * <ul>
 *   <li>Current day: {@code logs/mcp-audit.log}</li>
 *   <li>Rotated:     {@code logs/mcp-audit.2026-03-23.log}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * McpAuditFileLogger fileLogger = new McpAuditFileLogger(Path.of("logs"));
 * McpAuditLog auditLog = new McpAuditLog();
 * auditLog.setFileLogger(fileLogger);
 * // All audit entries are now written to logs/mcp-audit.log
 * }</pre>
 */
public class McpAuditFileLogger implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpAuditFileLogger.class);

    private static final String FILE_PREFIX = "mcp-audit";
    private static final String FILE_EXTENSION = ".log";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path logDirectory;
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile BufferedWriter writer;
    private volatile LocalDate currentDate;

    /**
     * Create an audit file logger that writes to the given directory.
     * The directory is created if it does not exist.
     *
     * @param logDirectory directory for audit log files (e.g. {@code logs/})
     */
    public McpAuditFileLogger(Path logDirectory) {
        this.logDirectory = logDirectory;
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            log.error("Failed to create MCP audit log directory: {}", logDirectory, e);
        }
        log.info("MCP audit file logger initialized: directory={}", logDirectory.toAbsolutePath());
    }

    /**
     * Write a single audit entry as a JSON line.
     * Automatically rotates the file at midnight.
     */
    public void write(McpAuditEntry entry) {
        writeLock.lock();
        try {
            ensureWriter();
            if (writer != null) {
                writer.write(entry.toJson());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log.error("Failed to write MCP audit entry to file", e);
            closeWriter();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Ensure the writer is open for the current date. Rotates if date has changed.
     */
    private void ensureWriter() throws IOException {
        LocalDate today = LocalDate.now();
        if (writer != null && today.equals(currentDate)) {
            return; // Writer is current
        }

        // Rotate: close old writer and rename current file
        if (writer != null) {
            closeWriter();
            // Rename previous day's file to dated name
            Path currentFile = logDirectory.resolve(FILE_PREFIX + FILE_EXTENSION);
            if (currentDate != null && Files.exists(currentFile)) {
                Path rotatedFile = logDirectory.resolve(
                        FILE_PREFIX + "." + currentDate.format(DATE_FMT) + FILE_EXTENSION);
                try {
                    Files.move(currentFile, rotatedFile);
                    log.info("Rotated MCP audit log: {} -> {}", currentFile, rotatedFile);
                } catch (IOException e) {
                    log.warn("Failed to rotate MCP audit log file: {}", e.getMessage());
                }
            }
        }

        // Open new writer
        Path currentFile = logDirectory.resolve(FILE_PREFIX + FILE_EXTENSION);
        writer = Files.newBufferedWriter(currentFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        currentDate = today;
        log.debug("Opened MCP audit log file: {}", currentFile);
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Error closing MCP audit log writer", e);
            }
            writer = null;
        }
    }

    @Override
    public void close() {
        writeLock.lock();
        try {
            closeWriter();
        } finally {
            writeLock.unlock();
        }
    }
}
