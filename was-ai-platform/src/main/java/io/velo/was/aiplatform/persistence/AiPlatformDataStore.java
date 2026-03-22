package io.velo.was.aiplatform.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI 플랫폼 데이터 파일 저장소.
 * Jackson 기반 JSON 파일 영속화를 제공하며, CRUD 변경 시 자동 flush (write-through).
 *
 * 저장 위치: {workDir}/data/ai-platform/
 * 파일 구조:
 *   - models.json      (모델 레지스트리 런타임 추가분)
 *   - tenants.json     (테넌트 + API 키)
 *   - keywords.json    (의도 키워드)
 *   - policies.json    (라우팅 정책)
 *   - audit.json       (감사 로그)
 */
public class AiPlatformDataStore {

    private static final Logger LOG = Logger.getLogger(AiPlatformDataStore.class.getName());

    private final Path dataDir;
    private final ObjectMapper mapper;

    public AiPlatformDataStore(Path workDir) {
        this.dataDir = workDir.resolve("data").resolve("ai-platform");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ensureDirectory();
    }

    /**
     * 객체를 JSON 파일로 저장.
     */
    public <T> void save(String fileName, T data) {
        try {
            Path file = dataDir.resolve(fileName);
            // UTF-8 인코딩을 명시적으로 지정 (Windows CP949 방지)
            byte[] jsonBytes = mapper.writeValueAsBytes(data);
            java.nio.file.Files.write(file, jsonBytes,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOG.fine(() -> "Saved " + fileName + " (" + jsonBytes.length + " bytes, UTF-8)");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save " + fileName, e);
        }
    }

    /**
     * JSON 파일에서 객체를 로드.
     */
    public <T> T load(String fileName, Class<T> type) {
        try {
            Path file = dataDir.resolve(fileName);
            if (!Files.exists(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            T result = mapper.readValue(bytes, type);
            LOG.fine(() -> "Loaded " + fileName);
            return result;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load " + fileName, e);
            return null;
        }
    }

    /**
     * JSON 파일에서 List를 로드.
     */
    public <T> List<T> loadList(String fileName, TypeReference<List<T>> typeRef) {
        try {
            Path file = dataDir.resolve(fileName);
            if (!Files.exists(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            List<T> result = mapper.readValue(bytes, typeRef);
            LOG.fine(() -> "Loaded " + fileName + " (" + result.size() + " items)");
            return result;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load " + fileName, e);
            return null;
        }
    }

    /**
     * ObjectMapper 인스턴스 반환 (커스텀 직렬화용).
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * 파일 존재 여부 확인.
     */
    public boolean exists(String fileName) {
        return Files.exists(dataDir.resolve(fileName));
    }

    /**
     * 데이터 디렉토리 경로 반환.
     */
    public Path getDataDir() {
        return dataDir;
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(dataDir);
            LOG.info("AI Platform data directory: " + dataDir.toAbsolutePath());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to create data directory: " + dataDir, e);
        }
    }
}
