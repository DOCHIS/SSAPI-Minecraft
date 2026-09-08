package kr.ssapi.storage;

import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * YAML 기반 StorageDriver 구현체 — 외부 DB 없이 파일만으로 동작.
 *
 * <p>연동 정보는 connections.yml, 후원 로그는 logs/donations-YYYY-MM-DD.jsonl(JSONL).
 * 일자별 로그 파일이 10MB 초과 시 타임스탬프 이름의 아카이브로 자동 로테이션.
 */
public class YamlDriver implements StorageDriver {
    private static final long LOG_ROTATE_BYTES = 10L * 1024 * 1024;

    private final File dataFile;
    private final File logFile;
    private final File logArchiveDir;
    private YamlConfiguration yaml;
    private final Gson gson;

    public YamlDriver(File dataFolder) {
        this.dataFile = new File(dataFolder, "connections.yml");
        File logsDir = new File(dataFolder, "logs");
        this.logFile = new File(logsDir, "donations-" + LocalDateTime.now().toLocalDate() + ".jsonl");
        this.logArchiveDir = new File(logsDir, "archive");

        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
                @Override
                public void write(JsonWriter out, LocalDateTime value) throws IOException {
                    out.value(value == null ? null : value.toString());
                }

                @Override
                public LocalDateTime read(JsonReader in) throws IOException {
                    return LocalDateTime.parse(in.nextString());
                }
            })
            .disableHtmlEscaping()
            .create();

        if (!logFile.getParentFile().exists()) logFile.getParentFile().mkdirs();
        if (!logArchiveDir.exists()) logArchiveDir.mkdirs();
    }

    @Override
    public synchronized void initialize() {
        if (!dataFile.exists()) {
            yaml = new YamlConfiguration();
            save();
        } else {
            yaml = YamlConfiguration.loadConfiguration(dataFile);
            if (migrateLegacyConnections()) {
                save();
                java.util.logging.Logger.getLogger("SSApi")
                    .info("legacy connections.yml 형식을 v2 primary 구조로 마이그레이션했습니다.");
            }
        }
    }

    private boolean migrateLegacyConnections() {
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        if (connections == null) return false;

        boolean changed = false;
        for (String uuid : connections.getKeys(false)) {
            String base = "connections." + uuid;
            if (!yaml.contains(base + ".platform")) continue;
            String primary = base + ".primary";
            if (!yaml.contains(primary + ".platform")) {
                yaml.set(primary + ".platform", yaml.getString(base + ".platform"));
                yaml.set(primary + ".streamerId", yaml.getString(base + ".streamerId"));
                yaml.set(primary + ".streamerName", yaml.getString(base + ".streamerName"));
                yaml.set(primary + ".name", yaml.getString(base + ".name"));
                yaml.set(primary + ".createdAt", yaml.getString(base + ".createdAt"));
            }
            if (!yaml.contains(base + ".enabled")) yaml.set(base + ".enabled", true);
            yaml.set(base + ".platform", null);
            yaml.set(base + ".streamerId", null);
            yaml.set(base + ".streamerName", null);
            yaml.set(base + ".name", null);
            yaml.set(base + ".createdAt", null);
            changed = true;
        }
        for (String uuid : connections.getKeys(false)) {
            String base = "connections." + uuid;
            boolean hasTypedConnection = false;
            for (ApiConnection.ConnectionType t : ApiConnection.ConnectionType.values()) {
                String path = base + "." + t.name().toLowerCase();
                if (!yaml.contains(path + ".platform")) continue;
                hasTypedConnection = true;
                if (yaml.contains(path + ".enabled")) {
                    if (!yaml.contains(base + ".enabled")) {
                        yaml.set(base + ".enabled", yaml.getBoolean(path + ".enabled", true));
                    }
                    yaml.set(path + ".enabled", null);
                    changed = true;
                }
            }
            if (hasTypedConnection && !yaml.contains(base + ".enabled")) {
                yaml.set(base + ".enabled", true);
                changed = true;
            }
        }
        return changed;
    }

    // yaml 객체를 dataFile(connections.yml)로 저장
    private void save() {
        try {
            yaml.save(dataFile);
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("SSApi")
                .log(java.util.logging.Level.WARNING, "YamlDriver save 실패", e);
        }
    }

    @Override public synchronized void close() { save(); }

    @Override
    public synchronized void saveConnection(ApiConnection connection) {
        String typeKey = connection.getConnectionType().name().toLowerCase();
        String path = "connections." + connection.getUuid() + "." + typeKey;
        yaml.set(path + ".platform", connection.getPlatform().name());
        yaml.set(path + ".streamerId", connection.getStreamerId());
        yaml.set(path + ".streamerName", connection.getStreamerName());
        yaml.set(path + ".name", connection.getName());
        yaml.set(path + ".createdAt", connection.getCreatedAt().toString());
        if (!yaml.contains("connections." + connection.getUuid() + ".enabled")) {
            yaml.set("connections." + connection.getUuid() + ".enabled", connection.isEnabled());
        }
        save();
    }

    @Override
    public synchronized void setEnabled(String uuid, boolean enabled) {
        yaml.set("connections." + uuid + ".enabled", enabled);
        save();
    }

    @Override
    public synchronized Optional<ApiConnection> getConnectionByUuidAndType(String uuid, ApiConnection.ConnectionType type) {
        String path = "connections." + uuid + "." + type.name().toLowerCase();
        if (!yaml.contains(path + ".platform")) return Optional.empty();
        return Optional.of(loadConnection(uuid, type, path));
    }

    @Override
    public synchronized List<ApiConnection> getConnectionsByUuid(String uuid) {
        List<ApiConnection> out = new ArrayList<>();
        for (ApiConnection.ConnectionType t : ApiConnection.ConnectionType.values()) {
            getConnectionByUuidAndType(uuid, t).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public synchronized Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId,
                                                                       ApiConnection.Platform platform) {
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        if (connections == null) return Optional.empty();

        for (String uuid : connections.getKeys(false)) {
            for (ApiConnection.ConnectionType t : ApiConnection.ConnectionType.values()) {
                String path = "connections." + uuid + "." + t.name().toLowerCase();
                if (Objects.equals(yaml.getString(path + ".streamerId"), streamerId)
                    && Objects.equals(yaml.getString(path + ".platform"), platform.name())) {
                    return Optional.of(loadConnection(uuid, t, path));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<ApiConnection> getAllConnections() {
        List<ApiConnection> result = new ArrayList<>();
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        if (connections == null) return result;
        for (String uuid : connections.getKeys(false)) {
            for (ApiConnection.ConnectionType t : ApiConnection.ConnectionType.values()) {
                String path = "connections." + uuid + "." + t.name().toLowerCase();
                if (yaml.contains(path + ".platform")) {
                    result.add(loadConnection(uuid, t, path));
                }
            }
        }
        return result;
    }

    @Override
    public synchronized List<ApiConnection> getConnectionsByPlatform(ApiConnection.Platform platform) {
        List<ApiConnection> result = new ArrayList<>();
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        if (connections == null) return result;
        for (String uuid : connections.getKeys(false)) {
            for (ApiConnection.ConnectionType t : ApiConnection.ConnectionType.values()) {
                String path = "connections." + uuid + "." + t.name().toLowerCase();
                if (Objects.equals(yaml.getString(path + ".platform"), platform.name())) {
                    result.add(loadConnection(uuid, t, path));
                }
            }
        }
        return result;
    }

    @Override
    public synchronized void deleteConnection(ApiConnection connection) {
        String typeKey = connection.getConnectionType().name().toLowerCase();
        yaml.set("connections." + connection.getUuid() + "." + typeKey, null);
        ConfigurationSection cs = yaml.getConfigurationSection("connections." + connection.getUuid());
        if (cs == null || cs.getKeys(false).isEmpty()) {
            yaml.set("connections." + connection.getUuid(), null);
        }
        save();
    }

    // YAML 경로에서 ApiConnection 객체를 읽어 반환
    private ApiConnection loadConnection(String uuid, ApiConnection.ConnectionType type, String path) {
        return new ApiConnection(
            uuid,
            ApiConnection.Platform.valueOf(yaml.getString(path + ".platform")),
            yaml.getString(path + ".streamerId"),
            yaml.getString(path + ".streamerName"),
            yaml.getString(path + ".name"),
            LocalDateTime.parse(yaml.getString(path + ".createdAt")),
            type,
            yaml.getBoolean("connections." + uuid + ".enabled", true)
        );
    }

    @Override
    public synchronized void saveApiLog(ApiLog log) {
        File dailyLog = new File(logFile.getParentFile(),
            "donations-" + LocalDateTime.now().toLocalDate() + ".jsonl");
        rotateIfTooLarge(dailyLog);
        try {
            Files.writeString(dailyLog.toPath(), gson.toJson(log) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("SSApi")
                .log(java.util.logging.Level.WARNING, "YamlDriver log 저장 실패", e);
        }
    }

    // 일자별 로그 파일이 10MB 이상이면 아카이브 디렉토리로 이동
    private void rotateIfTooLarge(File targetLog) {
        try {
            if (!targetLog.exists() || targetLog.length() < LOG_ROTATE_BYTES) return;
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File archive = new File(logArchiveDir, "donations-" + stamp + ".jsonl");
            Files.move(targetLog.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            java.util.logging.Logger.getLogger("SSApi")
                .log(java.util.logging.Level.WARNING, "log 로테이션 실패", e);
        }
    }
}
