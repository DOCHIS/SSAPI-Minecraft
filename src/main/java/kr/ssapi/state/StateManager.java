package kr.ssapi.state;

import kr.ssapi.kits.KitManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 메모리 상태를 디스크로 flush / 디스크에서 atomic load.
 *
 * <p>{@code /api관리 저장} 가 호출. {@code onDisable} 에서도 자동 호출.
 * {@code /api관리 리로드} 도 자동으로 save 선행.
 *
 * <p>저장 위치: {@code plugins/SSApi/state/} 하위 (사용자 편집 영역과 분리).
 */
public class StateManager {
    private final JavaPlugin plugin;
    private final KitManager kits;
    private final TriggerStats stats;
    private final File stateDir;

    public StateManager(JavaPlugin plugin, KitManager kits, TriggerStats stats) {
        this.plugin = plugin;
        this.kits = kits;
        this.stats = stats;
        this.stateDir = new File(plugin.getDataFolder(), "state");
        if (!stateDir.exists()) stateDir.mkdirs();
    }

    /** 모든 메모리 상태를 디스크로 저장 */
    public void saveAll() {
        try {
            kits.saveAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "kits 저장 실패", e);
        }
        saveStats();
    }

    // 트리거 발화 통계를 trigger-stats.yml 로 직렬화
    private void saveStats() {
        File f = new File(stateDir, "trigger-stats.yml");
        FileConfiguration cfg = new YamlConfiguration();
        Map<String, Long> snap = stats.snapshot();
        for (Map.Entry<String, Long> e : snap.entrySet()) {
            cfg.set("counters." + sanitize(e.getKey()), e.getValue());
        }
        try {
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "trigger-stats 저장 실패", e);
        }
    }

    // 디스크에 저장된 상태를 메모리로 불러옴
    public void loadAll() {
        loadStats();
    }

    private void loadStats() {
        File f = new File(stateDir, "trigger-stats.yml");
        if (!f.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        Map<String, Long> data = new HashMap<>();
        if (cfg.isConfigurationSection("counters")) {
            for (String k : cfg.getConfigurationSection("counters").getKeys(false)) {
                data.put(unsanitize(k), cfg.getLong("counters." + k));
            }
        }
        stats.load(data);
    }

    // YAML 키에 사용할 수 없는 특수문자(. :)를 escape 처리
    private String sanitize(String key) {
        return key.replace(".", "__DOT__").replace(":", "__COLON__");
    }

    // sanitize 된 키를 원래 형태로 복원
    private String unsanitize(String key) {
        return key.replace("__DOT__", ".").replace("__COLON__", ":");
    }
}
