package kr.ssapi.actions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 트리거의 actions: 항목 — 어떤 종류의 액션인지 + 파라미터.
 *
 * <p>예:
 * <pre>{@code
 * - type: command
 *   lines: ["say {donator_name} 감사", "give {player} diamond 1"]
 * - type: give_kit
 *   kit: vip_kit
 * - type: spawn_mob
 *   difficulty: 4
 * }</pre>
 */
public class ActionSpec {
    public final String type;
    public final Map<String, Object> params;
    public final List<String> lines;     // command 액션의 lines:
    public final List<Long> delaysTicks; // command 액션의 delays_ticks: (옵셔널)

    public ActionSpec(String type, Map<String, Object> params, List<String> lines, List<Long> delaysTicks) {
        this.type = type;
        this.params = params == null ? Collections.emptyMap() : params;
        this.lines = lines == null ? Collections.emptyList() : lines;
        this.delaysTicks = delaysTicks == null ? Collections.emptyList() : delaysTicks;
    }

    @SuppressWarnings("unchecked")
    public <T> T param(String key, T def) {
        Object v = params.get(key);
        if (v == null) return def;
        try { return (T) v; }
        catch (ClassCastException e) { return def; }
    }

    public String paramString(String key, String def) {
        Object v = params.get(key);
        return v == null ? def : v.toString();
    }
}
