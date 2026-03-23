package io.velo.was.aiplatform.audit;

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
 * AI 게이트웨이 감사 로그를 일별 로테이션 파일에 기록하는 파일 로거.
 *
 * <p>출력 형식: 한 줄에 하나의 JSON 오브젝트 (JSON Lines / NDJSON),
 * 로그 수집 도구(Fluentd, Filebeat, Promtail 등)와 연동에 적합하다.
 *
 * <h3>파일 명명</h3>
 * <ul>
 *   <li>당일: {@code logs/ai-gateway-audit.log}</li>
 *   <li>로테이션: {@code logs/ai-gateway-audit.2026-03-23.log}</li>
 * </ul>
 */
public class AiGatewayAuditFileLogger implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayAuditFileLogger.class);

    private static final String FILE_PREFIX = "ai-gateway-audit";
    private static final String FILE_EXTENSION = ".log";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path logDirectory;
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile BufferedWriter writer;
    private volatile LocalDate currentDate;

    /**
     * 지정된 디렉터리에 감사 파일 로거를 생성한다.
     * 디렉터리가 존재하지 않으면 자동 생성한다.
     *
     * @param logDirectory 감사 로그 파일 디렉터리 (예: {@code logs/})
     */
    public AiGatewayAuditFileLogger(Path logDirectory) {
        this.logDirectory = logDirectory;
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            log.error("Failed to create AI gateway audit log directory: {}", logDirectory, e);
        }
        log.info("AI gateway audit file logger initialized: directory={}", logDirectory.toAbsolutePath());
    }

    /**
     * 단일 감사 엔트리를 JSON 라인으로 기록한다.
     * 자정에 자동으로 파일을 로테이션한다.
     */
    public void write(AiGatewayAuditEntry entry) {
        writeLock.lock();
        try {
            ensureWriter();
            if (writer != null) {
                writer.write(entry.toJson());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log.error("Failed to write AI gateway audit entry to file", e);
            closeWriter();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 현재 날짜에 맞는 writer를 보장한다. 날짜가 변경되면 로테이션한다.
     */
    private void ensureWriter() throws IOException {
        LocalDate today = LocalDate.now();
        if (writer != null && today.equals(currentDate)) {
            return;
        }

        // 로테이션: 이전 writer 닫고 파일 이름 변경
        if (writer != null) {
            closeWriter();
            Path currentFile = logDirectory.resolve(FILE_PREFIX + FILE_EXTENSION);
            if (currentDate != null && Files.exists(currentFile)) {
                Path rotatedFile = logDirectory.resolve(
                        FILE_PREFIX + "." + currentDate.format(DATE_FMT) + FILE_EXTENSION);
                try {
                    Files.move(currentFile, rotatedFile);
                    log.info("Rotated AI gateway audit log: {} -> {}", currentFile, rotatedFile);
                } catch (IOException e) {
                    log.warn("Failed to rotate AI gateway audit log file: {}", e.getMessage());
                }
            }
        }

        // 새 writer 열기
        Path currentFile = logDirectory.resolve(FILE_PREFIX + FILE_EXTENSION);
        writer = Files.newBufferedWriter(currentFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        currentDate = today;
        log.debug("Opened AI gateway audit log file: {}", currentFile);
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Error closing AI gateway audit log writer", e);
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
