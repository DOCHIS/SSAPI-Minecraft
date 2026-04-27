package kr.ssapi.actions;

import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 즉사 액션 — 대상 플레이어를 즉시 사망시킨다.
 *
 * <p>config.yml 의 {@code actions.instant_death.protect_inventory} 가 true 이면
 * 죽기 직전 보조 손에 토템을 강제 지급해 아이템 손실 없이 부활시킴.
 * 사망 후 전 플레이어에게 브로드캐스트 메시지 전송.
 */
public class InstantDeathAction implements Action {
    private final JavaPlugin plugin;
    private final MessageService messages;

    public InstantDeathAction(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public void execute(ActionSpec spec, ActionContext context) {
        Player player = context.player;
        if (player == null || !player.isOnline()) return;

        boolean protectInventory = plugin.getConfig().getBoolean("actions.instant_death.protect_inventory", true);
        boolean soundEnabled = plugin.getConfig().getBoolean("sounds.instant_death", true);

        if (soundEnabled) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        }

        if (protectInventory) {
            startProtectedDeath(player);
        } else {
            player.setHealth(0);
        }

        broadcastDeath(player, context.placeholders);
    }

    // 타이틀 애니메이션 출력 후 토템을 강제 지급해 아이템 손실 없이 사망 처리
    private void startProtectedDeath(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack offHandItem = inventory.getItemInOffHand();
        boolean needsRestore = offHandItem != null && offHandItem.getType() != Material.AIR;
        ItemStack backupItem = needsRestore ? offHandItem.clone() : null;

        String title = messages.legacy("action.instant_death.protect_inventory_title");
        new BukkitRunnable() {
            private int currentChar = 0;
            private final StringBuilder displayText = new StringBuilder();

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (currentChar >= title.length()) {
                    cancel();
                    Bukkit.getScheduler().runTaskLater(plugin,
                        () -> giveTotemAndDamage(player, inventory, backupItem, needsRestore), 40L);
                    return;
                }
                displayText.append(title.charAt(currentChar));
                player.sendTitle(displayText.toString(), "", 0, 20, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
                currentChar++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // 보조 손에 토템을 장착하고 치명 데미지를 가한 뒤 토템을 원래 아이템으로 복원
    private void giveTotemAndDamage(Player player, PlayerInventory inventory, ItemStack backupItem, boolean needsRestore) {
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = totem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.legacy("totem.display"));
            meta.setLore(Arrays.asList(
                messages.legacy("totem.lore_1"),
                messages.legacy("totem.lore_2"),
                messages.legacy("totem.lore_3")
            ));
            totem.setItemMeta(meta);
        }

        double damage = player.getMaxHealth() * 2;
        inventory.setItemInOffHand(totem);
        player.damage(damage);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack currentOffHandItem = inventory.getItemInOffHand();
            if (currentOffHandItem != null && currentOffHandItem.getType() == Material.TOTEM_OF_UNDYING) {
                inventory.setItemInOffHand(needsRestore ? backupItem : new ItemStack(Material.AIR));
            }
        }, 1L);
    }

    // 사망 메시지를 온라인 전체 플레이어에게 브로드캐스트
    private void broadcastDeath(Player player, Map<String, String> placeholders) {
        String donator = placeholders.getOrDefault("donator_name", "익명");
        Map<String, String> map = new HashMap<>();
        map.put("player", player.getName());
        map.put("donator", donator);
        for (Player p : Bukkit.getOnlinePlayers()) {
            messages.send(p, "action.instant_death.broadcast", map);
        }
    }
}
