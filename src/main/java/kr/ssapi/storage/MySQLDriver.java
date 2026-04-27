package kr.ssapi.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 기반 StorageDriver 구현체 — HikariCP 커넥션 풀 사용.
 *
 * <p>초기화 시 api_connection / api_log 테이블이 없으면 자동 생성.
 * 호스트/DB명/포트 유효성을 화이트리스트 정규식으로 검증해 SQL Injection 방지.
 */
public class MySQLDriver implements StorageDriver {
    private HikariDataSource dataSource;

    @Override
    public void initialize() {
        FileConfiguration cfg = JavaPlugin.getProvidingPlugin(MySQLDriver.class).getConfig();

        String host = cfg.getString("storage.mysql.host", "localhost");
        String database = cfg.getString("storage.mysql.database", "ssapi");
        int port = cfg.getInt("storage.mysql.port", 3306);
        if (host == null || !host.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid MySQL host: " + host);
        }
        if (database == null || !database.matches("^[A-Za-z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid MySQL database: " + database);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid MySQL port: " + port);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        hikariConfig.setUsername(cfg.getString("storage.mysql.username", ""));
        hikariConfig.setPassword(cfg.getString("storage.mysql.password", ""));
        hikariConfig.setMaximumPoolSize(cfg.getInt("storage.mysql.pool.maximum_pool_size", 10));
        hikariConfig.setMinimumIdle(cfg.getInt("storage.mysql.pool.minimum_idle", 5));
        hikariConfig.setIdleTimeout(cfg.getLong("storage.mysql.pool.idle_timeout_ms", 300000L));
        hikariConfig.setMaxLifetime(cfg.getLong("storage.mysql.pool.max_lifetime_ms", 1800000L));
        hikariConfig.setConnectionTimeout(cfg.getLong("storage.mysql.pool.connection_timeout_ms", 30000L));

        dataSource = new HikariDataSource(hikariConfig);

        try {
            createTableIfNotExists();
            createApiLogTableIfNotExists();
        } catch (SQLException e) {
            throw new RuntimeException("MySQL 초기화 실패", e);
        }
    }

    // api_connection 테이블이 없으면 생성 (uuid + connection_type 복합 PK)
    private void createTableIfNotExists() throws SQLException {
        String sql =
            "CREATE TABLE IF NOT EXISTS `api_connection` (" +
            " `uuid` VARCHAR(50) NOT NULL," +
            " `connection_type` ENUM('primary','simulcast') NOT NULL DEFAULT 'primary'," +
            " `platform` ENUM('치지직','숲') NULL," +
            " `streamer_id` VARCHAR(100) NULL," +
            " `streamer_name` VARCHAR(100) NULL," +
            " `name` VARCHAR(16) NULL," +
            " `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP," +
            " PRIMARY KEY (`uuid`, `connection_type`)," +
            " INDEX `platform` (`platform`)," +
            " INDEX `streamer_id` (`streamer_id`)" +
            ") COLLATE='utf8mb4_general_ci' ENGINE=InnoDB;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    // api_log 테이블이 없으면 생성 (log_no AUTO_INCREMENT PK)
    private void createApiLogTableIfNotExists() throws SQLException {
        String sql =
            "CREATE TABLE IF NOT EXISTS `api_log` (" +
            " `log_no` INT(10) UNSIGNED NOT NULL AUTO_INCREMENT," +
            " `server_ip` VARCHAR(15) NULL DEFAULT NULL," +
            " `server_name` VARCHAR(100) NULL DEFAULT NULL," +
            " `streamer_id` VARCHAR(100) NULL DEFAULT NULL," +
            " `username` VARCHAR(100) NULL DEFAULT NULL," +
            " `cnt` INT(10) UNSIGNED NULL DEFAULT NULL," +
            " `type` VARCHAR(50) NULL DEFAULT NULL," +
            " `property` TEXT NULL DEFAULT NULL," +
            " `isRun` ENUM('Y','N') NULL DEFAULT 'Y'," +
            " `player_name` VARCHAR(100) NULL DEFAULT NULL," +
            " `player_uuid` CHAR(36) NULL DEFAULT NULL," +
            " `player_world` VARCHAR(100) NULL DEFAULT NULL," +
            " `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP," +
            " PRIMARY KEY (`log_no`)," +
            " INDEX `idx_streamer_id` (`streamer_id`)," +
            " INDEX `idx_player_uuid` (`player_uuid`)," +
            " INDEX `idx_created_at` (`created_at`)," +
            " INDEX `idx_isRun` (`isRun`)" +
            ") COLLATE='utf8mb4_general_ci' ENGINE=InnoDB;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    @Override
    public void close() {
        if (dataSource != null) dataSource.close();
    }

    @Override
    public void saveConnection(ApiConnection connection) {
        String sql =
            "INSERT INTO api_connection (uuid, connection_type, platform, streamer_id, streamer_name, name) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE platform=VALUES(platform), streamer_id=VALUES(streamer_id), " +
            "streamer_name=VALUES(streamer_name), name=VALUES(name)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, connection.getUuid());
            stmt.setString(2, connection.getConnectionType().name().toLowerCase());
            stmt.setString(3, connection.getPlatform().name());
            stmt.setString(4, connection.getStreamerId());
            stmt.setString(5, connection.getStreamerName());
            stmt.setString(6, connection.getName());
            stmt.executeUpdate();
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver saveConnection 실패", e);
        }
    }

    @Override
    public Optional<ApiConnection> getConnectionByUuidAndType(String uuid, ApiConnection.ConnectionType type) {
        String sql = "SELECT * FROM api_connection WHERE uuid = ? AND connection_type = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, type.name().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver getConnectionByUuidAndType 실패", e);
        }
        return Optional.empty();
    }

    @Override
    public List<ApiConnection> getConnectionsByUuid(String uuid) {
        List<ApiConnection> out = new ArrayList<>();
        String sql = "SELECT * FROM api_connection WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) out.add(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver getConnectionsByUuid 실패", e);
        }
        return out;
    }

    @Override
    public Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId, ApiConnection.Platform platform) {
        String sql = "SELECT * FROM api_connection WHERE streamer_id = ? AND platform = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, streamerId);
            stmt.setString(2, platform.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver getConnectionByStreamerIdAndPlatform 실패", e);
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
            while (rs.next()) connections.add(mapResultSetToConnection(rs));
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver getAllConnections 실패", e);
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
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) connections.add(mapResultSetToConnection(rs));
            }
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver getConnectionsByPlatform 실패", e);
        }
        return connections;
    }

    @Override
    public void deleteConnection(ApiConnection connection) {
        String sql = "DELETE FROM api_connection WHERE uuid = ? AND connection_type = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, connection.getUuid());
            stmt.setString(2, connection.getConnectionType().name().toLowerCase());
            stmt.executeUpdate();
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver deleteConnection(ApiConnection) 실패", e);
        }
    }

    @Override
    public void saveApiLog(ApiLog log) {
        String sql =
            "INSERT INTO api_log (server_ip, server_name, streamer_id, username, cnt, type, property, isRun, player_name, player_uuid, player_world) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, log.getServerIp());
            stmt.setString(2, log.getServerName());
            stmt.setString(3, log.getStreamerId());
            stmt.setString(4, log.getUsername());
            stmt.setInt(5, log.getCnt() == null ? 0 : log.getCnt());
            stmt.setString(6, log.getType());
            stmt.setString(7, log.getProperty());
            stmt.setString(8, log.getIsRun() == null ? "Y" : log.getIsRun().name());
            stmt.setString(9, log.getPlayerName());
            stmt.setString(10, log.getPlayerUuid());
            stmt.setString(11, log.getPlayerWorld());
            stmt.executeUpdate();
        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("SSApi").log(java.util.logging.Level.WARNING, "MySQLDriver saveApiLog 실패", e);
        }
    }

    // ResultSet 한 행을 ApiConnection 객체로 변환
    private ApiConnection mapResultSetToConnection(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (ts == null || rs.wasNull()) ? LocalDateTime.now() : ts.toLocalDateTime();

        ApiConnection.ConnectionType type = ApiConnection.ConnectionType.fromString(rs.getString("connection_type"));

        return new ApiConnection(
            rs.getString("uuid"),
            ApiConnection.Platform.valueOf(rs.getString("platform")),
            rs.getString("streamer_id"),
            rs.getString("streamer_name"),
            rs.getString("name"),
            createdAt,
            type
        );
    }
}
