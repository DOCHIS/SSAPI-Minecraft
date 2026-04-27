package kr.ssapi.actions;

import kr.ssapi.kits.Kit;
import kr.ssapi.kits.KitManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 킷 지급 액션. {@code kit:} 파라미터로 킷 이름 지정.
 *
 * <p>인벤토리가 가득 차면 config.yml [5] actions.give_kit.drop_overflow 설정에 따라
 * 바닥에 떨어뜨리거나 무시.
 */
public class GiveKitAction implements Action {
    private final JavaPlugin plugin;
    private final KitManager kits;

    public GiveKitAction(JavaPlugin plugin, KitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    @Override
    public void execute(ActionSpec spec, ActionContext context) {
        Player player = context.player;
        if (player == null || !player.isOnline()) return;

        // kit 파라미터 유효성 검사 후 메인 스레드에서 아이템 지급
        String kitName = spec.paramString("kit", null);
        if (kitName == null) {
            plugin.getLogger().warning("give_kit 액션에 kit: 누락");
            return;
        }
        Kit kit = kits.get(kitName);
        if (kit == null) {
            plugin.getLogger().warning("give_kit: 알 수 없는 킷 - " + kitName);
            return;
        }

        boolean dropOverflow = plugin.getConfig().getBoolean("actions.give_kit.drop_overflow", true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                kit.getItems().values().stream().map(ItemStack::clone).toArray(ItemStack[]::new)
            );
            if (overflow != null && !overflow.isEmpty() && dropOverflow) {
                Location loc = player.getLocation();
                for (ItemStack item : overflow.values()) {
                    player.getWorld().dropItemNaturally(loc, item);
                }
            }
        });
    }
}
