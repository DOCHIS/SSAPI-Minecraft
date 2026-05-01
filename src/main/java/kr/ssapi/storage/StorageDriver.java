package kr.ssapi.storage;

import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import java.util.List;
import java.util.Optional;

/**
 * 연동 정보 및 로그의 영구 저장소 추상 인터페이스.
 *
 * <p>구현체: {@link YamlDriver} (기본), {@link MySQLDriver}.
 * StorageManager 가 config.yml 의 storage.type 에 따라 선택.
 */
public interface StorageDriver {
    void initialize();
    void close();

    void saveConnection(ApiConnection connection);
    void deleteConnection(ApiConnection connection);
    void setEnabled(String uuid, boolean enabled);

    Optional<ApiConnection> getConnectionByUuidAndType(String uuid, ApiConnection.ConnectionType type);
    List<ApiConnection> getConnectionsByUuid(String uuid);
    Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId, ApiConnection.Platform platform);
    List<ApiConnection> getAllConnections();
    List<ApiConnection> getConnectionsByPlatform(ApiConnection.Platform platform);

    void saveApiLog(ApiLog log);
}
