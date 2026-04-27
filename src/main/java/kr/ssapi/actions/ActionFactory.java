package kr.ssapi.actions;

import kr.ssapi.kits.KitManager;
import kr.ssapi.services.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 액션 타입 문자열과 Action 구현체를 매핑하는 팩토리.
 *
 * <p>생성 시점에 모든 내장 액션을 registry 에 등록.
 * 알 수 없는 type 은 경고 로그만 출력하고 무시.
 */
public class ActionFactory {
    private final JavaPlugin plugin;
    private final Map<String, Action> registry = new HashMap<>();

    public ActionFactory(JavaPlugin plugin, KitManager kits, MessageService messages) {
        this.plugin = plugin;
        registry.put("command", new CommandAction(plugin));
        registry.put("give_kit", new GiveKitAction(plugin, kits));
        registry.put("spawn_mob", new SpawnMobAction(plugin, messages));
        registry.put("random_effect", new RandomEffectAction(plugin, messages));
        registry.put("random_teleport", new RandomTeleportAction(plugin, messages));
        registry.put("instant_death", new InstantDeathAction(plugin, messages));
    }

    // spec 의 type 을 registry 에서 찾아 해당 Action 을 실행
    public void execute(ActionSpec spec, ActionContext context) {
        Action a = registry.get(spec.type);
        if (a == null) {
            plugin.getLogger().warning("알 수 없는 action type: " + spec.type);
            return;
        }
        a.execute(spec, context);
    }
}
