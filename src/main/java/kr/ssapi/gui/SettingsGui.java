package kr.ssapi.gui;

import kr.ssapi.config.MissionSettings;
import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 설정 GUI — 플러그인 주요 설정 값을 클릭으로 토글.
 *
 * <p>오프라인 실행 여부 / 정산 방식 / 디버그 모드 / 미션 hook_timings 표시.
 * 변경 즉시 config.yml 저장 후 화면을 갱신.
 */
public class SettingsGui implements GuiScreen {
    private final GuiManager gui;
    private final MessageService messages;

    public SettingsGui(GuiManager gui, MessageService messages) {
        this.gui = gui;
        this.messages = messages;
    }

    @Override public String name() { return "settings"; }

    @Override
    public Inventory build(Player viewer, SsapiGuiHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27, messages.legacy("gui.settings.title"));
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SettingsGui.class);

        boolean executeOffline = plugin.getConfig().getBoolean("reward.execute_when_offline", false);
        inv.setItem(10, GuiUtil.icon(executeOffline ? Material.LIME_DYE : Material.GRAY_DYE,
            messages.legacy("gui.settings.offline_label", "value", String.valueOf(executeOffline)),
            messages.legacy("gui.settings.toggle_hint")));

        String payout = plugin.getConfig().getString("mission.settle_payout", "combined");
        inv.setItem(12, GuiUtil.icon(Material.GOLD_INGOT,
            messages.legacy("gui.settings.payout_label", "value", payout),
            messages.legacy("gui.settings.payout_hint")));

        boolean debug = plugin.getConfig().getBoolean("logging.debug", false);
        inv.setItem(14, GuiUtil.icon(debug ? Material.REDSTONE : Material.GUNPOWDER,
            messages.legacy("gui.settings.debug_label", "value", String.valueOf(debug)),
            messages.legacy("gui.settings.toggle_hint")));

        MissionSettings.Snapshot s = MissionSettings.get();
        inv.setItem(16, GuiUtil.icon(Material.NETHER_STAR,
            messages.legacy("gui.settings.mission_label", "value", String.valueOf(s.hookTimings)),
            messages.legacy("gui.settings.mission_hint_1"),
            messages.legacy("gui.settings.mission_hint_2")));

        inv.setItem(18, GuiUtil.backButton(messages));
        GuiUtil.fillEmpty(inv);
        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, SsapiGuiHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SettingsGui.class);

        switch (event.getRawSlot()) {
            case 10: {
                boolean cur = plugin.getConfig().getBoolean("reward.execute_when_offline", false);
                plugin.getConfig().set("reward.execute_when_offline", !cur);
                plugin.saveConfig();
                break;
            }
            case 12: {
                String cur = plugin.getConfig().getString("mission.settle_payout", "combined");
                String next = "combined".equals(cur) ? "individual" : "combined";
                plugin.getConfig().set("mission.settle_payout", next);
                plugin.saveConfig();
                break;
            }
            case 14: {
                boolean cur = plugin.getConfig().getBoolean("logging.debug", false);
                plugin.getConfig().set("logging.debug", !cur);
                plugin.saveConfig();
                break;
            }
            case 16:
                messages.send(p, "gui.settings.chat_dashboard_link");
                p.closeInventory();
                return;
            case 18:
                p.closeInventory();
                return;
            default:
                return;
        }
        gui.open(p, this, null);
    }
}
