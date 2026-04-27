package kr.ssapi.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * messages.yml 의 모든 사용자 노출 문자열을 관리.
 *
 * <p>코드 내 하드코딩 메시지 0건 정책. 모든 메시지는 messages.yml 의 키로 정의되고
 * 본 서비스를 통해서만 접근.
 *
 * <p>특징:
 * <ul>
 *   <li>MiniMessage 포맷 파싱
 *   <li>prefix 자동 prepend (메시지 본문에 prefix 중복 작성 금지)
 *   <li>placeholder 치환 ({@code <name>} 형식)
 *   <li>키 없거나 빈 문자열이면 메시지 출력 안 함 (사용자가 비우면 표시 안 됨)
 * </ul>
 */
public class MessageService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    private Component prefix = Component.empty();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final File file;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    /** messages.yml 다시 로드 */
    public void reload() {
        if (!file.exists()) {
            // resources 의 기본 messages.yml 복사
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        // 기본값 병합 (resources 의 messages.yml 의 키 중 user file 에 없는 것은 default 사용)
        try (InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                this.config.setDefaults(defaults);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "messages.yml 기본값 로드 실패", e);
        }

        // prefix 캐싱
        String prefixRaw = config.getString("prefix", "");
        this.prefix = prefixRaw.isEmpty() ? Component.empty() : mm.deserialize(prefixRaw);
    }

    /**
     * 메시지를 반환 (placeholder 치환). 키 없거나 빈 문자열이면 null.
     * prefix 는 자동으로 앞에 붙음.
     */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = config.getString(key);
        if (raw == null || raw.isEmpty()) return null;
        TagResolver[] resolvers = buildResolvers(placeholders);
        Component body = mm.deserialize(raw, resolvers);
        return prefix.append(body);
    }

    public Component get(String key) {
        return get(key, (Map<String, String>) null);
    }

    public Component get(String key, String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return get(key, map);
    }

    /** 메시지를 sender 에게 전송 (null 이면 전송 안 함) */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        Component msg = get(key, placeholders);
        if (msg == null) return;
        sender.sendMessage(msg);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, (Map<String, String>) null);
    }

    public void send(CommandSender sender, String key, String... kv) {
        Component msg = get(key, kv);
        if (msg == null) return;
        sender.sendMessage(msg);
    }

    public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        Component msg = get(key, placeholders);
        if (msg == null) return;
        player.sendActionBar(msg);
    }

    /** 키 존재 여부 (값이 빈 문자열이어도 true) */
    public boolean has(String key) {
        return config.contains(key);
    }

    /** 직접 raw 문자열 반환 (placeholder 치환 안 함) */
    public String getRaw(String key) {
        return config.getString(key, "");
    }

    /**
     * 레거시 §-코드 String 반환 (GUI ItemMeta / Inventory title 용).
     * MiniMessage 파싱 후 LegacyComponentSerializer 로 § 변환. prefix 미포함.
     */
    public String legacy(String key, Map<String, String> placeholders) {
        String raw = config.getString(key);
        if (raw == null || raw.isEmpty()) return "";
        TagResolver[] resolvers = buildResolvers(placeholders);
        Component body = mm.deserialize(raw, resolvers);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(body);
    }

    public String legacy(String key) { return legacy(key, (Map<String, String>) null); }

    public String legacy(String key, String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return legacy(key, map);
    }

    /** 자식 키 (예: action.give_kit.success → action.give_kit 의 자식들) */
    public ConfigurationSection getSection(String key) {
        return config.getConfigurationSection(key);
    }

    // placeholder 맵을 MiniMessage TagResolver 배열로 변환
    private TagResolver[] buildResolvers(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return new TagResolver[0];
        TagResolver[] out = new TagResolver[placeholders.size()];
        int i = 0;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            out[i++] = Placeholder.unparsed(e.getKey(), value);
        }
        return out;
    }
}
