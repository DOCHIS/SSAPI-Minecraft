package kr.ssapi.kits;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * kits.yml 파일을 읽고 쓰는 싱글톤 관리자.
 *
 * <p>저장 포맷: ItemStack.serialize() 의 ConfigurationSerializable YAML 표현.
 *
 * <p>GUI 편집 시 자동 저장. 동시 편집 락 (player UUID 단위, 15초 TTL).
 */
public class KitManager {
    private static final String HEADER = String.join("\n",
        "════════════════════════════════════════════════════════════════════",
        "SSAPI 킷 - 후원자에게 줄 아이템 묶음",
        "════════════════════════════════════════════════════════════════════",
        "편집은 게임 안에서 /API관리 -> 킷 메뉴를 사용하는 것을 권장합니다.",
        "인벤토리에 아이템을 드래그하면 킷에 자동으로 저장됩니다.",
        "",
        "items 값은 마인크래프트 아이템 데이터를 직렬화한 내용입니다.",
        "손으로 수정하기 어렵고, 잘못 고치면 아이템이 깨질 수 있습니다.",
        "",
        "킷 이름과 표시 이름은 필요하면 직접 바꿀 수 있습니다.",
        "예시:",
        "kits:",
        "  starter:",
        "    display: \"스타터 킷\"",
        "    items: {}",
        "════════════════════════════════════════════════════════════════════"
    );

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Long> editLocks = new ConcurrentHashMap<>();
    private static final long LOCK_TTL_MS = 15_000L;

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        reload();
    }

    // kits.yml 을 처음부터 다시 읽어 메모리에 적재
    public void reload() {
        kits.clear();
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            saveAll();
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("kits");
        if (root == null && file.length() == 0L) {
            saveAll();
            return;
        }
        if (root == null) return;

        for (String name : root.getKeys(false)) {
            ConfigurationSection k = root.getConfigurationSection(name);
            if (k == null) continue;
            Kit kit = new Kit(name, k.getString("display", name));
            ConfigurationSection items = k.getConfigurationSection("items");
            if (items != null) {
                for (String slotStr : items.getKeys(false)) {
                    int slot;
                    try { slot = Integer.parseInt(slotStr); }
                    catch (NumberFormatException e) { continue; }
                    Object raw = items.get(slotStr);
                    ItemStack item = deserialize(raw);
                    if (item != null) kit.setSlot(slot, item);
                }
            }
            kits.put(name, kit);
        }
    }

    // 메모리의 모든 킷을 kits.yml 로 직렬화해 저장
    public synchronized void saveAll() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.options().header(HEADER);
        cfg.options().copyHeader(true);
        if (kits.isEmpty()) {
            cfg.set("kits", new HashMap<String, Object>());
        }
        for (Map.Entry<String, Kit> e : kits.entrySet()) {
            String prefix = "kits." + e.getKey() + ".";
            cfg.set(prefix + "display", e.getValue().getDisplay());
            for (Map.Entry<Integer, ItemStack> s : e.getValue().getItems().entrySet()) {
                cfg.set(prefix + "items." + s.getKey(), s.getValue().serialize());
            }
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "kits.yml 저장 실패", ex);
        }
    }

    public Kit get(String name) { return kits.get(name); }
    public Map<String, Kit> all() { return Collections.unmodifiableMap(kits); }

    public synchronized void put(Kit kit) {
        kits.put(kit.getName(), kit);
        saveAll();
    }

    public synchronized void remove(String name) {
        kits.remove(name);
        saveAll();
    }

    /**
     * 편집 락 획득. 다른 OP 가 같은 킷을 편집 중이면 false 반환.
     */
    public synchronized boolean acquireEditLock(java.util.UUID editor, String kitName) {
        cleanExpiredLocks();
        for (Map.Entry<java.util.UUID, Long> e : editLocks.entrySet()) {
            if (!e.getKey().equals(editor) && System.currentTimeMillis() - e.getValue() < LOCK_TTL_MS) {
                return false;
            }
        }
        editLocks.put(editor, System.currentTimeMillis());
        return true;
    }

    // 편집 락 해제 — GUI 닫힐 때 호출
    public synchronized void releaseEditLock(java.util.UUID editor) {
        editLocks.remove(editor);
    }

    private void cleanExpiredLocks() {
        long now = System.currentTimeMillis();
        editLocks.entrySet().removeIf(e -> now - e.getValue() > LOCK_TTL_MS);
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserialize(Object raw) {
        if (!(raw instanceof Map)) return null;
        try {
            return ItemStack.deserialize((Map<String, Object>) raw);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "킷 아이템 deserialize 실패: " + e.getMessage());
            return null;
        }
    }
}
