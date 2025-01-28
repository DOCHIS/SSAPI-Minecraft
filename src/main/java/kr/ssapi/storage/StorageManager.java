package kr.ssapi.storage;

import kr.ssapi.config.Config;
import org.bukkit.plugin.java.JavaPlugin;

public class StorageManager {
    private static StorageDriver driver;
    
    public static void initialize(JavaPlugin plugin) {
        Config config = Config.getInstance();
        
        if ("mysql".equalsIgnoreCase(config.getStorageType())) {
            driver = new MySQLDriver();
        } else {
            driver = new YamlDriver(plugin.getDataFolder());
        }
        
        driver.initialize();
    }
    
    public static StorageDriver getDriver() {
        return driver;
    }
    
    public static void close() {
        if (driver != null) {
            driver.close();
        }
    }
} 