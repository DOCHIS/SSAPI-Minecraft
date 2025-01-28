package kr.ssapi.model;

import java.time.LocalDateTime;

public class ApiLog {
    private Long logNo;
    private String serverIp;
    private String serverName;
    private String streamerId;
    private String username;
    private Integer cnt;
    private String type;
    private String property;
    private IsRun isRun;
    private String playerName;
    private String playerUuid;
    private String playerWorld;
    private LocalDateTime createdAt;

    public enum IsRun {
        Y, N
    }

    // 생성자
    public ApiLog(Long logNo, String serverIp, String serverName, String streamerId, 
                 String username, Integer cnt, String type, String property, 
                 IsRun isRun, String playerName, String playerUuid, 
                 String playerWorld, LocalDateTime createdAt) {
        this.logNo = logNo;
        this.serverIp = serverIp;
        this.serverName = serverName;
        this.streamerId = streamerId;
        this.username = username;
        this.cnt = cnt;
        this.type = type;
        this.property = property;
        this.isRun = isRun;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.playerWorld = playerWorld;
        this.createdAt = createdAt;
    }

    // Getter 메서드들
    public Long getLogNo() { return logNo; }
    public String getServerIp() { return serverIp; }
    public String getServerName() { return serverName; }
    public String getStreamerId() { return streamerId; }
    public String getUsername() { return username; }
    public Integer getCnt() { return cnt; }
    public String getType() { return type; }
    public String getProperty() { return property; }
    public IsRun getIsRun() { return isRun; }
    public String getPlayerName() { return playerName; }
    public String getPlayerUuid() { return playerUuid; }
    public String getPlayerWorld() { return playerWorld; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setter 메서드들
    public void setLogNo(Long logNo) { this.logNo = logNo; }
    public void setServerIp(String serverIp) { this.serverIp = serverIp; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public void setStreamerId(String streamerId) { this.streamerId = streamerId; }
    public void setUsername(String username) { this.username = username; }
    public void setCnt(Integer cnt) { this.cnt = cnt; }
    public void setType(String type) { this.type = type; }
    public void setProperty(String property) { this.property = property; }
    public void setIsRun(IsRun isRun) { this.isRun = isRun; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public void setPlayerWorld(String playerWorld) { this.playerWorld = playerWorld; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}