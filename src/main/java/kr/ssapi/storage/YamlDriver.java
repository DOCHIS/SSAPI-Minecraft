package kr.ssapi.storage;

import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;


public class YamlDriver implements StorageDriver {
    private final File dataFile;
    private final File logFile;  // 단일 로그 파일
    private YamlConfiguration yaml;
    private final Gson gson;
    
    public YamlDriver(File dataFolder) {
        this.dataFile = new File(dataFolder, "connections.yml");
        this.logFile = new File(dataFolder, "logs.txt");  // 단일 로그 파일 경로
        
        // Gson 인스턴스 생성 시 LocalDateTime 어댑터 추가
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
                @Override
                public void write(JsonWriter out, LocalDateTime value) throws IOException {
                    out.value(value.toString());
                }

                @Override
                public LocalDateTime read(JsonReader in) throws IOException {
                    return LocalDateTime.parse(in.nextString());
                }
            })
            .create();
            
        // 로그 파일의 부모 디렉토리가 없다면 생성
        if (!logFile.getParentFile().exists()) {
            logFile.getParentFile().mkdirs();
        }
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
    
    private void save() {
        try {
            yaml.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        save();
    }

    @Override
    public void saveConnection(ApiConnection connection) {
        String path = "connections." + connection.getUuid();
        yaml.set(path + ".platform", connection.getPlatform().name());
        yaml.set(path + ".streamerId", connection.getStreamerId());
        yaml.set(path + ".streamerName", connection.getStreamerName());
        yaml.set(path + ".name", connection.getName());
        yaml.set(path + ".createdAt", connection.getCreatedAt().toString());
        save();
    }

    @Override
    public Optional<ApiConnection> getConnectionByUuid(String uuid) {
        String path = "connections." + uuid;
        if (!yaml.contains(path)) {
            return Optional.empty();
        }
        
        return Optional.of(loadConnection(path));
    }

    @Override
    public Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId, ApiConnection.Platform platform) {
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        if (connections == null) {
            return Optional.empty();
        }

        for (String uuid : connections.getKeys(false)) {
            String path = "connections." + uuid;
            if (yaml.getString(path + ".streamerId").equals(streamerId) &&
                yaml.getString(path + ".platform").equals(platform.name())) {
                return Optional.of(loadConnection(path));
            }
        }
        
        return Optional.empty();
    }

    @Override
    public List<ApiConnection> getAllConnections() {
        List<ApiConnection> result = new ArrayList<>();
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        
        if (connections != null) {
            for (String uuid : connections.getKeys(false)) {
                result.add(loadConnection("connections." + uuid));
            }
        }
        
        return result;
    }

    @Override
    public List<ApiConnection> getConnectionsByPlatform(ApiConnection.Platform platform) {
        List<ApiConnection> result = new ArrayList<>();
        ConfigurationSection connections = yaml.getConfigurationSection("connections");
        
        if (connections != null) {
            for (String uuid : connections.getKeys(false)) {
                String path = "connections." + uuid;
                if (yaml.getString(path + ".platform").equals(platform.name())) {
                    result.add(loadConnection(path));
                }
            }
        }
        
        return result;
    }

    @Override
    public void deleteConnection(String uuid) {
        yaml.set("connections." + uuid, null);
        save();
    }

    private ApiConnection loadConnection(String path) {
        return new ApiConnection(
            path.substring(path.lastIndexOf('.') + 1),
            ApiConnection.Platform.valueOf(yaml.getString(path + ".platform")),
            yaml.getString(path + ".streamerId"),
            yaml.getString(path + ".streamerName"),
            yaml.getString(path + ".name"),
            LocalDateTime.parse(yaml.getString(path + ".createdAt"))
        );
    }

    @Override
    public void saveApiLog(ApiLog log) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String jsonLog = gson.toJson(log);
            writer.write(jsonLog);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

} 