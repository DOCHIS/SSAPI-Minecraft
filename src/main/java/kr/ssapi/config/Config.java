package kr.ssapi.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class Config {
    private static Config instance;
    private final JavaPlugin plugin;
    private FileConfiguration config;

    // API 관련 설정
    private String prefix;
    private boolean donationCommandEnabled;
    private String donationCommandFormat;
    private boolean streamerNickCommandEnabled;
    private String streamerNickCommandFormat;
    private String apiKey;
    private String apiServer;
    private String socketServer;

    // 스토리지 관련 설정
    private String storageType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlMaxPoolSize;
    private int mysqlMinIdle;
    private long mysqlIdleTimeout;
    private long mysqlMaxLifetime;
    private long mysqlConnectionTimeout;

    // 메시지 관련 설정
    private String messageConnectSuccess;
    private String messageConnectFail;
    private String messageDisconnectSuccess;
    private String messageDisconnectFail;

    // 소켓 관련 설정
    private int socketTimeout;
    private boolean socketReconnectionEnabled;
    private int socketReconnectionAttempts;
    private int socketReconnectionDelay;
    private int socketReconnectionMaxDelay;
    private int socketLoginRetryDelay;

    // 후원 액션 관련 설정
    private String giveItemData;
    private Map<String, Boolean> spawnMobEnabled;
    private int spawnMobDifficulty;
    private RandomTeleportConfig randomTeleportConfig;
    private boolean instantDeathProtectInventory;
    private boolean instantDeathBroadcastEnabled;
    private String instantDeathBroadcastMessage;

    private static class RandomTeleportConfig {
        private final RangeConfig xRange;
        private final RangeConfig zRange;
        private final YRangeConfig yRange;
        private final boolean allowWater;
        private final boolean allowLava;
        private final boolean allowSolid;

        private static class RangeConfig {
            private final int minDistance;
            private final int maxDistance;
            private final int worldBorderMin;
            private final int worldBorderMax;

            public RangeConfig(FileConfiguration config, String path) {
                this.minDistance = config.getInt(path + ".distance.min", 3000);
                this.maxDistance = config.getInt(path + ".distance.max", 6000);
                this.worldBorderMin = config.getInt(path + ".world-border.min", -30000);
                this.worldBorderMax = config.getInt(path + ".world-border.max", 30000);
            }

            public int getRandomDistance() {
                return (int) (Math.random() * (maxDistance - minDistance + 1)) + minDistance;
            }

            public int getMinDistance() { return minDistance; }
            public int getMaxDistance() { return maxDistance; }
            public int getWorldBorderMin() { return worldBorderMin; }
            public int getWorldBorderMax() { return worldBorderMax; }
        }

        private static class YRangeConfig {
            private final int worldBorderMin;
            private final int worldBorderMax;
            private final boolean bottomToTop; // Y축 탐색 방향

            public YRangeConfig(FileConfiguration config, String path) {
                this.worldBorderMin = config.getInt(path + ".world-border.min", 64);
                this.worldBorderMax = config.getInt(path + ".world-border.max", 256);
                String direction = config.getString(path + ".search-direction", "BOTTOM_TO_TOP");
                this.bottomToTop = direction.equals("BOTTOM_TO_TOP");
            }

            public int getWorldBorderMin() { return worldBorderMin; }
            public int getWorldBorderMax() { return worldBorderMax; }
            public boolean isBottomToTop() { return bottomToTop; }
        }

        public RandomTeleportConfig(FileConfiguration config) {
            String basePath = "donation-actions.settings.random-teleport.range";
            
            this.xRange = new RangeConfig(config, basePath + ".x");
            this.zRange = new RangeConfig(config, basePath + ".z");
            this.yRange = new YRangeConfig(config, basePath + ".y");

            String safePath = "donation-actions.settings.random-teleport.safe-zone";
            this.allowWater = config.getBoolean(safePath + ".allow-water", false);
            this.allowLava = config.getBoolean(safePath + ".allow-lava", false);
            this.allowSolid = config.getBoolean(safePath + ".allow-solid", false);
        }

        // Getter 메소드들
        public int getRandomRangeX() { return xRange.getRandomDistance(); }
        public int getRandomRangeZ() { return zRange.getRandomDistance(); }
        public int getRandomRangeY() { return yRange.getWorldBorderMin(); }
        
        public int getMinX() { return xRange.getWorldBorderMin(); }
        public int getMaxX() { return xRange.getWorldBorderMax(); }
        public int getMinZ() { return zRange.getWorldBorderMin(); }
        public int getMaxZ() { return zRange.getWorldBorderMax(); }
        public int getMinY() { return yRange.getWorldBorderMin(); }
        public int getMaxY() { return yRange.getWorldBorderMax(); }
        
        public boolean isAllowWater() { return allowWater; }
        public boolean isAllowLava() { return allowLava; }
        public boolean isAllowSolid() { return allowSolid; }

        public boolean isYSearchBottomToTop() { return yRange.isBottomToTop(); }
    }

    private Config(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new Config(plugin);
        }
    }

    public static Config getInstance() {
        return instance;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // API 설정 로드
        prefix = config.getString("api.prefix", "[API] ");
        apiKey = config.getString("api.key", "");
        
        // API 키 필수 검증
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API 키가 설정되지 않았습니다. config.yml 파일에서 'api.key' 값을 설정해주세요.");
        }

        // 스토리지 설정 로드 및 검증
        storageType = config.getString("storage.type", "yml");
        if (!storageType.equals("yml") && !storageType.equals("mysql")) {
            throw new IllegalStateException("잘못된 스토리지 타입입니다. 'yml' 또는 'mysql'만 사용 가능합니다.");
        }

        // MySQL 설정이 선택된 경우 필수 설정 검증
        if (storageType.equals("mysql")) {
            mysqlHost = config.getString("storage.mysql.host", "");
            mysqlDatabase = config.getString("storage.mysql.database", "");
            mysqlUsername = config.getString("storage.mysql.username", "");
            mysqlPassword = config.getString("storage.mysql.password", "");

            validateMySQLConfig();
        }

        // 나머지 설정 로드
        loadRemainingConfig();
    }

    private void validateMySQLConfig() {
        if (mysqlHost.isEmpty()) {
            throw new IllegalStateException("MySQL 호스트가 설정되지 않았습니다.");
        }
        if (mysqlDatabase.isEmpty() || mysqlDatabase.equals("your_database")) {
            throw new IllegalStateException("MySQL 데이터베이스가 설정되지 않았습니다.");
        }
        if (mysqlUsername.isEmpty() || mysqlUsername.equals("your_username")) {
            throw new IllegalStateException("MySQL 사용자 이름이 설정되지 않았습니다.");
        }
        if (mysqlPassword.isEmpty() || mysqlPassword.equals("your_password")) {
            throw new IllegalStateException("MySQL 비밀번호가 설정되지 않았습니다.");
        }
    }

    private void loadRemainingConfig() {
        // 기존의 나머지 설정들 로드
        donationCommandEnabled = config.getBoolean("api.commands.donation.enabled", true);
        donationCommandFormat = config.getString("api.commands.donation.format", "donation {platform} {player} {cnt} {username}");
        streamerNickCommandEnabled = config.getBoolean("api.commands.streamernick.enabled", true);
        streamerNickCommandFormat = config.getString("api.commands.streamernick.format", "setstreamernick {player} {nickname}");
        apiServer = config.getString("api.servers.api", "https://api.ssapi.kr");
        socketServer = config.getString("api.servers.socket", "https://socket.ssapi.kr");

        // MySQL 관련 나머지 설정
        mysqlPort = config.getInt("storage.mysql.port", 3306);
        mysqlMaxPoolSize = config.getInt("storage.mysql.pool.maximum-pool-size", 10);
        mysqlMinIdle = config.getInt("storage.mysql.pool.minimum-idle", 5);
        mysqlIdleTimeout = config.getLong("storage.mysql.pool.idle-timeout", 300000);
        mysqlMaxLifetime = config.getLong("storage.mysql.pool.max-lifetime", 1800000);
        mysqlConnectionTimeout = config.getLong("storage.mysql.pool.connection-timeout", 30000);

        // 메시지 설정 로드
        messageConnectSuccess = config.getString("messages.api.connect-success", "");
        messageConnectFail = config.getString("messages.api.connect-fail", "");
        messageDisconnectSuccess = config.getString("messages.api.disconnect-success", "");
        messageDisconnectFail = config.getString("messages.api.disconnect-fail", "");

        // 소켓 설정 로드
        socketTimeout = config.getInt("api.socket.timeout", 5000);
        socketReconnectionEnabled = config.getBoolean("api.socket.reconnection.enabled", true);
        socketReconnectionAttempts = config.getInt("api.socket.reconnection.attempts", Integer.MAX_VALUE);
        socketReconnectionDelay = config.getInt("api.socket.reconnection.delay", 1000);
        socketReconnectionMaxDelay = config.getInt("api.socket.reconnection.max-delay", 5000);
        socketLoginRetryDelay = config.getInt("api.socket.login-retry-delay", 2000);

        // 후원 액션 설정 로드
        giveItemData = config.getString("donation-actions.settings.give-item.item-data", "");
        
        // 몹 스폰 설정
        spawnMobEnabled = new HashMap<>();
        spawnMobEnabled.put("passive", config.getBoolean("donation-actions.settings.spawn-mob.enabled.passive", true));
        spawnMobEnabled.put("neutral", config.getBoolean("donation-actions.settings.spawn-mob.enabled.neutral", true));
        spawnMobEnabled.put("hostile", config.getBoolean("donation-actions.settings.spawn-mob.enabled.hostile", true));
        spawnMobEnabled.put("boss", config.getBoolean("donation-actions.settings.spawn-mob.enabled.boss", true));
        spawnMobDifficulty = config.getInt("donation-actions.settings.spawn-mob.difficulty", 3);

        // 랜덤 텔레포트 설정
        randomTeleportConfig = new RandomTeleportConfig(config);

        // 즉시 사망 설정
        instantDeathProtectInventory = config.getBoolean("donation-actions.settings.instant-death.protect-inventory", true);
        instantDeathBroadcastEnabled = config.getBoolean("donation-actions.settings.instant-death.broadcast.enabled", true);
        instantDeathBroadcastMessage = config.getString("messages.instant-death.broadcast", 
            "§c{player}§f님이 §e{donator}§f님의 §c즉시 사망§f 후원으로 인해 사망하셨습니다!");
    }

    // Getter 메소드들
    public String getPrefix() { return prefix; }
    public boolean isDonationCommandEnabled() { return donationCommandEnabled; }
    public String getDonationCommandFormat() { return donationCommandFormat; }
    public boolean isStreamerNickCommandEnabled() { return streamerNickCommandEnabled; }
    public String getStreamerNickCommandFormat() { return streamerNickCommandFormat; }
    public String getApiKey() { return apiKey; }
    public String getApiServer() { return apiServer; }
    public String getSocketServer() { return socketServer; }
    public String getStorageType() { return storageType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public int getMysqlMaxPoolSize() { return mysqlMaxPoolSize; }
    public int getMysqlMinIdle() { return mysqlMinIdle; }
    public long getMysqlIdleTimeout() { return mysqlIdleTimeout; }
    public long getMysqlMaxLifetime() { return mysqlMaxLifetime; }
    public long getMysqlConnectionTimeout() { return mysqlConnectionTimeout; }
    public String getMessageConnectSuccess() { return messageConnectSuccess; }
    public String getMessageConnectFail() { return messageConnectFail; }
    public String getMessageDisconnectSuccess() { return messageDisconnectSuccess; }
    public String getMessageDisconnectFail() { return messageDisconnectFail; }

    // 소켓 설정 getter 메소드들
    public int getSocketTimeout() { return socketTimeout; }
    public boolean isSocketReconnectionEnabled() { return socketReconnectionEnabled; }
    public int getSocketReconnectionAttempts() { 
        return socketReconnectionAttempts; 
    }
    public int getSocketReconnectionDelay() { return socketReconnectionDelay; }
    public int getSocketReconnectionMaxDelay() { return socketReconnectionMaxDelay; }
    public int getSocketLoginRetryDelay() { return socketLoginRetryDelay; }

    // 새로운 Getter 메소드들
    public String getGiveItemData() { return giveItemData; }
    public boolean isSpawnMobEnabled(String type) { return spawnMobEnabled.getOrDefault(type, false); }
    public int getSpawnMobDifficulty() { return spawnMobDifficulty; }
    public RandomTeleportConfig getRandomTeleportConfig() { return randomTeleportConfig; }
    public boolean isInstantDeathProtectInventory() { return instantDeathProtectInventory; }
    public boolean isInstantDeathBroadcastEnabled() { return instantDeathBroadcastEnabled; }
    public String getInstantDeathBroadcastMessage() { return instantDeathBroadcastMessage; }

    // RandomTeleportConfig의 getter 메소드들을 Config 클래스에 추가
    public int getTeleportRangeX() { return randomTeleportConfig.getRandomRangeX(); }
    public int getTeleportRangeZ() { return randomTeleportConfig.getRandomRangeZ(); }
    public int getTeleportRangeY() { return randomTeleportConfig.getRandomRangeY(); }
    public int getTeleportMinX() { return randomTeleportConfig.getMinX(); }
    public int getTeleportMaxX() { return randomTeleportConfig.getMaxX(); }
    public int getTeleportMinZ() { return randomTeleportConfig.getMinZ(); }
    public int getTeleportMaxZ() { return randomTeleportConfig.getMaxZ(); }
    public int getTeleportMinY() { return randomTeleportConfig.getMinY(); }
    public int getTeleportMaxY() { return randomTeleportConfig.getMaxY(); }
    public boolean isTeleportAllowWater() { return randomTeleportConfig.isAllowWater(); }
    public boolean isTeleportAllowLava() { return randomTeleportConfig.isAllowLava(); }
    public boolean isTeleportAllowSolid() { return randomTeleportConfig.isAllowSolid(); }

    // Getter 메소드들 이름 변경
    public int getTeleportXMinDistance() { return randomTeleportConfig.xRange.getMinDistance(); }
    public int getTeleportXMaxDistance() { return randomTeleportConfig.xRange.getMaxDistance(); }
    public int getTeleportXWorldBorderMin() { return randomTeleportConfig.xRange.getWorldBorderMin(); }
    public int getTeleportXWorldBorderMax() { return randomTeleportConfig.xRange.getWorldBorderMax(); }

    public int getTeleportZMinDistance() { return randomTeleportConfig.zRange.getMinDistance(); }
    public int getTeleportZMaxDistance() { return randomTeleportConfig.zRange.getMaxDistance(); }
    public int getTeleportZWorldBorderMin() { return randomTeleportConfig.zRange.getWorldBorderMin(); }
    public int getTeleportZWorldBorderMax() { return randomTeleportConfig.zRange.getWorldBorderMax(); }

    public int getTeleportYMinDistance() { return randomTeleportConfig.yRange.getWorldBorderMin(); }
    public int getTeleportYMaxDistance() { return randomTeleportConfig.yRange.getWorldBorderMax(); }
    public int getTeleportYWorldBorderMin() { return randomTeleportConfig.yRange.getWorldBorderMin(); }
    public int getTeleportYWorldBorderMax() { return randomTeleportConfig.yRange.getWorldBorderMax(); }

    // 설정 다시 로드하는 메소드
    public void reload() {
        loadConfig();
    }

    // 메시지 getter - 설정이 null일 때만 빈 문자열 반환
    public String getMessage(String path) {
        String message = config.getString(path);
        if (message == null) return "";
        return message;
    }

    // Config 클래스에 새로운 Getter 추가
    public boolean isTeleportYSearchBottomToTop() { 
        return randomTeleportConfig.isYSearchBottomToTop(); 
    }

    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    public long getLong(String path, long defaultValue) {
        return config.getLong(path, defaultValue);
    }

    // 즉시 사망 관련 새로운 getter 메소드들
    public String getInstantDeathProtectTitle() {
        return config.getString("messages.instant-death.protect-inventory.title", "인벤... 세이브!!");
    }

    public int getInstantDeathTypingSpeed() {
        return config.getInt("messages.instant-death.protect-inventory.typing-speed", 2);
    }

    public String getInstantDeathTypingSound() {
        return config.getString("messages.instant-death.protect-inventory.sound.type", "BLOCK_NOTE_BLOCK_HARP");
    }

    public double getInstantDeathTypingSoundVolume() {
        return config.getDouble("messages.instant-death.protect-inventory.sound.volume", 1.0);
    }

    public double getInstantDeathTypingSoundPitch() {
        return config.getDouble("messages.instant-death.protect-inventory.sound.pitch", 1.0);
    }

    public String getInstantDeathBroadcastSound() {
        return config.getString("messages.instant-death.broadcast.sound.type", "ENTITY_LIGHTNING_BOLT_THUNDER");
    }

    public double getInstantDeathBroadcastSoundVolume() {
        return config.getDouble("messages.instant-death.broadcast.sound.volume", 1.0);
    }

    public double getInstantDeathBroadcastSoundPitch() {
        return config.getDouble("messages.instant-death.broadcast.sound.pitch", 1.0);
    }
} 