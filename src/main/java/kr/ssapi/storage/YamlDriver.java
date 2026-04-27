package kr.ssapi.storage;

import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
 * <p>연동 정보는 connections.yml, 로그는 logs.txt(JSON Lines).
 * 로그 파일이 10MB 초과 시 타임스탬프 이름의 아카이브로 자동 로테이션.
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
        this.logFile = new File(dataFolder, "logs.txt");
        this.logArchiveDir = new File(dataFolder, "log-archive");

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
            .create();

        if (!logFile.getParentFile().exists()) logFile.getParentFile().mkdirs();
        if (!logArchiveDir.exists()) logArchiveDir.mkdirs();
    }

    @Override
    public void initialize() {
        if (!dataFile.exists()) {
            yaml = new YamlConfiguration();
            save();
        } else {
            yaml = YamlConfiguration.loadConfiguration(dataFile);
        }
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

    @Override public void close() { save(); }

    @Override
    public void saveConnection(ApiConnection connection) {
        String typeKey = connection.getConnectionType().name().toLowerCase();
        String path = "connections." + connection.getUuid() + "." + typeKey;
        yaml.set(path + ".platform", connection.getPlatform().name());
        yaml.set(path + ".streamerId", connection.getStreamerId());
        yaml.set(path + ".streamerName", connection.getStreamerName());
        yaml.set(path + ".name", connection.getName());
        yaml.set(path + ".createdAt", connection.getCreatedAt().toString());
        save();
    }

    @Override
    public Optional<ApiConnection> getConnectionByUuidAndType(String uuid, ApiConnection.ConnectionType type) {
        String path = "connections." + uuid + "." + type.name().toLowerCase();
        if (!yaml.contains(path + ".platform")) return Optional.empty();
        return Optional.of(loadConnection(uuid, type, path));
    }

    @Override
    public List<ApiConnection> getConnectionsByUuid(String uuid) {
        List<ApiConnection> out = new ArrayList<>();
        for (ApiConnection.ConnectionType t : ApiConnection.ConnectionType.values()) {
            getConnectionByUuidAndType(uuid, t).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId,
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
    public List<ApiConnection> getAllConnections() {
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
    public List<ApiConnection> getConnectionsByPlatform(ApiConnection.Platform platform) {
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
    public void deleteConnection(ApiConnection connection) {
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
            type
        );
    }

    @Override
    public void saveApiLog(ApiLog log) {
        rotateIfTooLarge();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(gson.toJson(log));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            java.util.logging.Logger.getLogger("SSApi")
                .log(java.util.logging.Level.WARNING, "YamlDriver log 저장 실패", e);
        }
    }

    // 로그 파일이 10MB 이상이면 아카이브 디렉토리로 이동
    private void rotateIfTooLarge() {
        try {
            if (!logFile.exists() || logFile.length() < LOG_ROTATE_BYTES) return;
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File archive = new File(logArchiveDir, "logs-" + stamp + ".txt");
            Files.move(logFile.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            java.util.logging.Logger.getLogger("SSApi")
                .log(java.util.logging.Level.WARNING, "log 로테이션 실패", e);
        }
    }
}
