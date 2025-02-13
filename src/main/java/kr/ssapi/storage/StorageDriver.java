package kr.ssapi.storage;

import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StorageDriver {
    void initialize();
    void close();
    
    // 스트리머 연동 정보 저장
    void saveConnection(ApiConnection connection);
    
    // 스트리머 연동 정보 삭제
    void deleteConnection(String uuid);
    
    // UUID로 연동 정보 조회
    Optional<ApiConnection> getConnectionByUuid(String uuid);
    
    // 스트리머 ID와 플랫폼으로 연동 정보 조회
    Optional<ApiConnection> getConnectionByStreamerIdAndPlatform(String streamerId, ApiConnection.Platform platform);
    
    // 모든 스트리머 목록 조회
    List<ApiConnection> getAllConnections();
    
    // 플랫폼별 스트리머 목록 조회
    List<ApiConnection> getConnectionsByPlatform(ApiConnection.Platform platform);
    
    // 후원 로그 저장
    void saveApiLog(ApiLog log);
} 