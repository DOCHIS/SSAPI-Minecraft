package kr.ssapi.model;

import java.time.LocalDateTime;

public class ApiConnection {
    private String uuid;
    private Platform platform;
    private String streamerId;
    private String streamerName;
    private String name;
    private LocalDateTime createdAt;

    public enum Platform {
        치지직, 숲;
        
        public static Platform fromString(String text) {
            try {
                return Platform.valueOf(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    // 생성자
    public ApiConnection(String uuid, Platform platform, String streamerId, String streamerName, String name, LocalDateTime createdAt) {
        this.uuid = uuid;
        this.platform = platform;
        this.streamerId = streamerId;
        this.streamerName = streamerName;
        this.name = name;
        this.createdAt = createdAt;
    }

    // Getter와 Setter
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
} 