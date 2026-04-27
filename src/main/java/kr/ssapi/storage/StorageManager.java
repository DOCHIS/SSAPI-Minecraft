package kr.ssapi.storage;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * StorageDriver 의 싱글톤 관리자.
 *
 * <p>플러그인 활성화 시 initialize() 로 드라이버를 선택·초기화하고,
 * 비활성화 시 close() 로 연결을 정리. getDriver() 로 어디서나 접근.
 */
public class StorageManager {
    private static StorageDriver driver;

    // storage.type 설정을 읽어 YamlDriver 또는 MySQLDriver 를 생성 및 초기화
    public static void initialize(JavaPlugin plugin) {
        String type = plugin.getConfig().getString("storage.type", "yml");
        if ("mysql".equalsIgnoreCase(type)) {
            driver = new MySQLDriver();
        } else {
            driver = new YamlDriver(plugin.getDataFolder());
        }
        driver.initialize();
    }

    public static StorageDriver getDriver() {
        if (driver == null) {
            throw new IllegalStateException("StorageManager not initialized");
        }
        return driver;
    }

    public static void close() {
        if (driver != null) driver.close();
    }
}
