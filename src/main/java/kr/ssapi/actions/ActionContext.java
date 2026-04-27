package kr.ssapi.actions;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 액션 실행 컨텍스트 — placeholder 치환용 변수 + 대상 플레이어.
 *
 * <p>donation/mission/connect 모든 트리거에서 공통 사용. 시점별 다른 변수
 * 채워서 전달.
 */
public class ActionContext {
    public final Player player;
    public final Map<String, String> placeholders;

    public ActionContext(Player player) {
        this.player = player;
        this.placeholders = new HashMap<>();
    }

    public ActionContext put(String key, String value) {
        placeholders.put(key, value == null ? "" : value);
        return this;
    }

    public String resolve(String template) {
        if (template == null) return "";
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
