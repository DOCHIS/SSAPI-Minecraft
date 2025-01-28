package kr.ssapi.storage;

import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YamlDriver implements StorageDriver {
    private final File dataFile;
    private final File logFile;
    private YamlConfiguration yaml;
    private YamlConfiguration logYaml;
    
    public YamlDriver(File dataFolder) {
        this.dataFile = new File(dataFolder, "connections.yml");
        this.logFile = new File(dataFolder, "logs.yml");
    }
    
    @Override
    public void initialize() {
        if (!dataFile.exists()) {
            yaml = new YamlConfiguration();
            save();
        } else {
            yaml = YamlConfiguration.loadConfiguration(dataFile);
        }

        if (!logFile.exists()) {
            logYaml = new YamlConfiguration();
            saveLog();
        } else {
            logYaml = YamlConfiguration.loadConfiguration(logFile);
        }
    }
    
    private void save() {
        try {
            yaml.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveLog() {
        try {
            logYaml.save(logFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        save();
        saveLog();
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
        // 현재 시간을 기반으로 고유한 키 생성 (밀리초 단위까지)
        String logKey = String.valueOf(System.currentTimeMillis());
        String path = "logs." + logKey;

        logYaml.set(path + ".serverIp", log.getServerIp());
        logYaml.set(path + ".serverName", log.getServerName());
        logYaml.set(path + ".streamerId", log.getStreamerId());
        logYaml.set(path + ".username", log.getUsername());
        logYaml.set(path + ".cnt", log.getCnt());
        logYaml.set(path + ".type", log.getType());
        logYaml.set(path + ".property", log.getProperty());
        logYaml.set(path + ".isRun", log.getIsRun().name());
        logYaml.set(path + ".playerName", log.getPlayerName());
        logYaml.set(path + ".playerUuid", log.getPlayerUuid());
        logYaml.set(path + ".playerWorld", log.getPlayerWorld());
        logYaml.set(path + ".createdAt", log.getCreatedAt().toString());
        
        saveLog();
    }

    @Override
    public List<ApiLog> getApiLogsByStreamerId(String streamerId) {
        List<ApiLog> logs = new ArrayList<>();
        ConfigurationSection logsSection = logYaml.getConfigurationSection("logs");
        
        if (logsSection != null) {
            for (String logNo : logsSection.getKeys(false)) {
                String path = "logs." + logNo;
                if (streamerId.equals(logYaml.getString(path + ".streamerId"))) {
                    logs.add(loadApiLog(path));
                }
            }
        }
        
        return logs;
    }

    @Override
    public List<ApiLog> getApiLogsByPlayerUuid(String playerUuid) {
        List<ApiLog> logs = new ArrayList<>();
        ConfigurationSection logsSection = logYaml.getConfigurationSection("logs");
        
        if (logsSection != null) {
            for (String logNo : logsSection.getKeys(false)) {
                String path = "logs." + logNo;
                if (playerUuid.equals(logYaml.getString(path + ".playerUuid"))) {
                    logs.add(loadApiLog(path));
                }
            }
        }
        
        return logs;
    }

    @Override
    public List<ApiLog> getApiLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        List<ApiLog> logs = new ArrayList<>();
        ConfigurationSection logsSection = logYaml.getConfigurationSection("logs");
        
        if (logsSection != null) {
            for (String logNo : logsSection.getKeys(false)) {
                String path = "logs." + logNo;
                LocalDateTime createdAt = LocalDateTime.parse(logYaml.getString(path + ".createdAt"));
                if (!createdAt.isBefore(start) && !createdAt.isAfter(end)) {
                    logs.add(loadApiLog(path));
                }
            }
        }
        
        return logs;
    }

    @Override
    public List<ApiLog> getPendingApiLogs() {
        List<ApiLog> logs = new ArrayList<>();
        ConfigurationSection logsSection = logYaml.getConfigurationSection("logs");
        
        if (logsSection != null) {
            for (String logNo : logsSection.getKeys(false)) {
                String path = "logs." + logNo;
                if ("N".equals(logYaml.getString(path + ".isRun"))) {
                    logs.add(loadApiLog(path));
                }
            }
        }
        
        return logs;
    }

    @Override
    public void updateApiLogStatus(Long logNo, ApiLog.IsRun status) {
        String path = "logs." + logNo;
        if (logYaml.contains(path)) {
            logYaml.set(path + ".isRun", status.name());
            saveLog();
        }
    }

    private ApiLog loadApiLog(String path) {
        return new ApiLog(
            Long.parseLong(path.substring(path.lastIndexOf('.') + 1)),
            logYaml.getString(path + ".serverIp"),
            logYaml.getString(path + ".serverName"),
            logYaml.getString(path + ".streamerId"),
            logYaml.getString(path + ".username"),
            logYaml.getInt(path + ".cnt"),
            logYaml.getString(path + ".type"),
            logYaml.getString(path + ".property"),
            ApiLog.IsRun.valueOf(logYaml.getString(path + ".isRun")),
            logYaml.getString(path + ".playerName"),
            logYaml.getString(path + ".playerUuid"),
            logYaml.getString(path + ".playerWorld"),
            LocalDateTime.parse(logYaml.getString(path + ".createdAt"))
        );
    }
} 