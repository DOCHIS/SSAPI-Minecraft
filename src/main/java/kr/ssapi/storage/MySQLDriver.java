package kr.ssapi.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.ssapi.config.Config;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MySQLDriver implements StorageDriver {
    private HikariDataSource dataSource;
    
    @Override
    public void initialize() {
        Config config = Config.getInstance();
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/" + config.getMysqlDatabase());
        hikariConfig.setUsername(config.getMysqlUsername());
        hikariConfig.setPassword(config.getMysqlPassword());
        hikariConfig.setMaximumPoolSize(config.getMysqlMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMysqlMinIdle());
        hikariConfig.setIdleTimeout(config.getMysqlIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMysqlMaxLifetime());
        hikariConfig.setConnectionTimeout(config.getMysqlConnectionTimeout());
        
        dataSource = new HikariDataSource(hikariConfig);
        
        createTableIfNotExists();
        createApiLogTableIfNotExists();
    }
    
    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS `api_connection` (
                `uuid` VARCHAR(50) NOT NULL,
                `platform` ENUM('치지직','숲') NULL,
                `streamer_id` VARCHAR(100) NULL,
                `streamer_name` VARCHAR(100) NULL,
                `name` VARCHAR(16) NULL,
                `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (`uuid`),
                INDEX `platform` (`platform`),
                INDEX `streamer_id` (`streamer_id`)
            ) COLLATE='utf8mb4_general_ci' ENGINE=InnoDB;
            """;
            
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createApiLogTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS `api_log` (
                `log_no` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
                `server_ip` VARCHAR(15) NULL DEFAULT NULL,
                `server_name` VARCHAR(100) NULL DEFAULT NULL,
                `streamer_id` VARCHAR(100) NULL DEFAULT NULL,
                `username` VARCHAR(100) NULL DEFAULT NULL,
                `cnt` INT(10) UNSIGNED NULL DEFAULT NULL,
                `type` VARCHAR(50) NULL DEFAULT NULL,
                `property` TEXT NULL DEFAULT NULL,
                `isRun` ENUM('Y','N') NULL DEFAULT 'Y',
                `player_name` VARCHAR(100) NULL DEFAULT NULL,
                `player_uuid` CHAR(36) NULL DEFAULT NULL,
                `player_world` VARCHAR(100) NULL DEFAULT NULL,
                `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (`log_no`)
            ) COLLATE='utf8mb4_general_ci' ENGINE=InnoDB;
            """;
            
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public void saveConnection(ApiConnection connection) {
        String sql = "INSERT INTO api_connection (uuid, platform, streamer_id, streamer_name, name) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, connection.getUuid());
            stmt.setString(2, connection.getPlatform().name());
            stmt.setString(3, connection.getStreamerId());
            stmt.setString(4, connection.getStreamerName());
            stmt.setString(5, connection.getName());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Optional<ApiConnection> getConnectionByUuid(String uuid) {
        String sql = "SELECT * FROM api_connection WHERE uuid = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId, ApiConnection.Platform platform) {
        String sql = "SELECT * FROM api_connection WHERE streamer_id = ? AND platform = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, streamerId);
            stmt.setString(2, platform.name());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public List<ApiConnection> getAllConnections() {
        List<ApiConnection> connections = new ArrayList<>();
        String sql = "SELECT * FROM api_connection";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                connections.add(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connections;
    }

    @Override
    public List<ApiConnection> getConnectionsByPlatform(ApiConnection.Platform platform) {
        List<ApiConnection> connections = new ArrayList<>();
        String sql = "SELECT * FROM api_connection WHERE platform = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, platform.name());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                connections.add(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connections;
    }

    @Override
    public void deleteConnection(String uuid) {
        String sql = "DELETE FROM api_connection WHERE uuid = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to delete connection", e);
        }
    }

    @Override
    public void saveApiLog(ApiLog log) {
        String sql = """
            INSERT INTO api_log (server_ip, server_name, streamer_id, username, 
            cnt, type, property, isRun, player_name, player_uuid, player_world)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, log.getServerIp());
            stmt.setString(2, log.getServerName());
            stmt.setString(3, log.getStreamerId());
            stmt.setString(4, log.getUsername());
            stmt.setInt(5, log.getCnt());
            stmt.setString(6, log.getType());
            stmt.setString(7, log.getProperty());
            stmt.setString(8, log.getIsRun().name());
            stmt.setString(9, log.getPlayerName());
            stmt.setString(10, log.getPlayerUuid());
            stmt.setString(11, log.getPlayerWorld());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<ApiLog> getApiLogsByStreamerId(String streamerId) {
        List<ApiLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM api_log WHERE streamer_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, streamerId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                logs.add(mapResultSetToApiLog(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    @Override
    public List<ApiLog> getApiLogsByPlayerUuid(String playerUuid) {
        List<ApiLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM api_log WHERE player_uuid = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                logs.add(mapResultSetToApiLog(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    @Override
    public List<ApiLog> getApiLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        List<ApiLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM api_log WHERE created_at BETWEEN ? AND ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                logs.add(mapResultSetToApiLog(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    @Override
    public List<ApiLog> getPendingApiLogs() {
        List<ApiLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM api_log WHERE isRun = 'N'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                logs.add(mapResultSetToApiLog(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    @Override
    public void updateApiLogStatus(Long logNo, ApiLog.IsRun status) {
        String sql = "UPDATE api_log SET isRun = ? WHERE log_no = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setLong(2, logNo);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private ApiConnection mapResultSetToConnection(ResultSet rs) throws SQLException {
        return new ApiConnection(
            rs.getString("uuid"),
            ApiConnection.Platform.valueOf(rs.getString("platform")),
            rs.getString("streamer_id"),
            rs.getString("streamer_name"),
            rs.getString("name"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private ApiLog mapResultSetToApiLog(ResultSet rs) throws SQLException {
        return new ApiLog(
            rs.getLong("log_no"),
            rs.getString("server_ip"),
            rs.getString("server_name"),
            rs.getString("streamer_id"),
            rs.getString("username"),
            rs.getInt("cnt"),
            rs.getString("type"),
            rs.getString("property"),
            ApiLog.IsRun.valueOf(rs.getString("isRun")),
            rs.getString("player_name"),
            rs.getString("player_uuid"),
            rs.getString("player_world"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
} 