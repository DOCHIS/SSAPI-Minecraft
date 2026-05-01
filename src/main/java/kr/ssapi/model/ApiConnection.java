package kr.ssapi.model;

import java.time.LocalDateTime;

/**
 * 스트리머-플레이어 연동 정보 모델.
 *
 * <p>플레이어 UUID, 플랫폼(숲/치지직), 스트리머 채널 ID, 연동 타입(PRIMARY/SIMULCAST)을 보관.
 */
public class ApiConnection {
    private String uuid;
    private Platform platform;
    private String streamerId;
    private String streamerName;
    private String name;
    private LocalDateTime createdAt;
    private ConnectionType connectionType;
    private boolean enabled;

    public enum Platform {
        치지직, 숲;

        public static Platform fromString(String text) {
            return Platform.valueOf(text);
        }

        public String toApiString() {
            switch (this) {
                case 숲:    return "soop";
                case 치지직: return "chzzk";
                default: throw new IllegalStateException("Unsupported platform: " + name());
            }
        }
    }

    public enum ConnectionType {
        PRIMARY, SIMULCAST;

        public static ConnectionType fromString(String text) {
            if (text == null) throw new IllegalArgumentException("connection_type is null");
            return ConnectionType.valueOf(text.toUpperCase());
        }
    }

    public ApiConnection(String uuid, Platform platform, String streamerId, String streamerName, String name, LocalDateTime createdAt, ConnectionType connectionType) {
        this(uuid, platform, streamerId, streamerName, name, createdAt, connectionType, true);
    }

    public ApiConnection(String uuid, Platform platform, String streamerId, String streamerName, String name, LocalDateTime createdAt, ConnectionType connectionType, boolean enabled) {
        if (connectionType == null) throw new IllegalArgumentException("connectionType required");
        this.uuid = uuid;
        this.platform = platform;
        this.streamerId = streamerId;
        this.streamerName = streamerName;
        this.name = name;
        this.createdAt = createdAt;
        this.connectionType = connectionType;
        this.enabled = enabled;
    }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public String getStreamerId() { return streamerId; }
    public void setStreamerId(String streamerId) { this.streamerId = streamerId; }

    public String getStreamerName() { return streamerName; }
    public void setStreamerName(String streamerName) { this.streamerName = streamerName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public ConnectionType getConnectionType() { return connectionType; }
    public void setConnectionType(ConnectionType connectionType) { this.connectionType = connectionType; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
