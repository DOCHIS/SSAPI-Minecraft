package kr.ssapi.triggers;

import kr.ssapi.actions.ActionSpec;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * triggers.yml 와 config.yml 의 mission.on_* 에서 트리거를 읽어 TriggerRegistry 에 등록.
 */
public class TriggerLoader {
    private final JavaPlugin plugin;

    public TriggerLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // triggers.yml 과 config.yml 의 모든 scope 를 파싱해 registry 에 등록
    public void loadInto(TriggerRegistry registry) {
        registry.clear();

        // triggers.yml 의 connect / donation
        File triggersFile = new File(plugin.getDataFolder(), "triggers.yml");
        if (triggersFile.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(triggersFile);
            registry.set(TriggerRegistry.Scope.CONNECT, parseScope(cfg, "connect"));
            registry.set(TriggerRegistry.Scope.DONATION, parseScope(cfg, "donation"));
        }

        // config.yml 의 mission.on_* (3개 scope)
        FileConfiguration cfg = plugin.getConfig();
        registry.set(TriggerRegistry.Scope.MISSION_RECEIVE, parseScope(cfg, "mission.on_receive"));
        registry.set(TriggerRegistry.Scope.MISSION_SETTLE, parseScope(cfg, "mission.on_settle"));
        registry.set(TriggerRegistry.Scope.MISSION_RESULT, parseScope(cfg, "mission.on_result"));
    }

    // 설정 파일의 특정 경로에서 트리거 목록을 파싱해 priority 순 정렬
    private List<Trigger> parseScope(FileConfiguration cfg, String path) {
        List<Trigger> out = new ArrayList<>();
        List<Map<?, ?>> raw = cfg.getMapList(path);
        for (Map<?, ?> entry : raw) {
            try {
                Trigger t = parseTrigger(entry);
                if (t != null) out.add(t);
            } catch (Exception e) {
                plugin.getLogger().warning("트리거 파싱 실패 (" + path + "): " + e.getMessage());
            }
        }
        // priority desc + id 사전순 정렬
        out.sort(Comparator.<Trigger>comparingInt(t -> -t.priority)
            .thenComparing(t -> t.id == null ? "" : t.id));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Trigger parseTrigger(Map<?, ?> entry) {
        Map<String, Object> e = (Map<String, Object>) entry;
        String id = String.valueOf(e.getOrDefault("id", "unnamed"));
        boolean enabled = Boolean.parseBoolean(String.valueOf(e.getOrDefault("enabled", "false")));
        int priority = ((Number) e.getOrDefault("priority", 0)).intValue();
        boolean stop = Boolean.parseBoolean(String.valueOf(e.getOrDefault("stop_on_match", "false")));

        MatchSpec match = parseMatch((Map<String, Object>) e.get("match"));
        List<ActionSpec> actions = new ArrayList<>();
        Object actionsObj = entry.get("actions");
        if (actionsObj instanceof List) {
            for (Object a : (List<?>) actionsObj) {
                if (a instanceof Map) {
                    actions.add(parseAction((Map<String, Object>) a));
                }
            }
        }
        return new Trigger(id, enabled, match, priority, stop, actions);
    }

    // match 맵의 op 값에 따라 적절한 MatchSpec 를 생성해 반환
    private MatchSpec parseMatch(Map<String, Object> m) {
        if (m == null) return MatchSpec.any();
        String op = String.valueOf(m.getOrDefault("op", "any")).toLowerCase();
        switch (op) {
            case "eq":    return MatchSpec.eq(((Number) m.getOrDefault("value", 0)).longValue());
            case "gte":   return MatchSpec.gte(((Number) m.getOrDefault("value", 0)).longValue());
            case "lte":   return MatchSpec.lte(((Number) m.getOrDefault("value", 0)).longValue());
            case "range": return MatchSpec.range(
                ((Number) m.getOrDefault("min", 0)).longValue(),
                ((Number) m.getOrDefault("max", Long.MAX_VALUE)).longValue());
            case "any":
            default:      return MatchSpec.any();
        }
    }

    private ActionSpec parseAction(Map<String, Object> m) {
        String type = String.valueOf(m.getOrDefault("type", "command"));
        Map<String, Object> options = new java.util.HashMap<>(m);
        options.remove("type");
        options.remove("lines");
        options.remove("delays_ticks");

        List<String> lines = new ArrayList<>();
        Object linesObj = m.get("lines");
        if (linesObj instanceof List) {
            for (Object o : (List<?>) linesObj) lines.add(String.valueOf(o));
        }

        List<Long> delays = new ArrayList<>();
        Object delaysObj = m.get("delays_ticks");
        if (delaysObj instanceof List) {
            for (Object o : (List<?>) delaysObj) {
                if (o instanceof Number) delays.add(((Number) o).longValue());
            }
        }
        return new ActionSpec(type, options, lines, delays);
    }
}
