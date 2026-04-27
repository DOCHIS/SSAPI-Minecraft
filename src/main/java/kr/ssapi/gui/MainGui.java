package kr.ssapi.gui;

import kr.ssapi.kits.KitManager;
import kr.ssapi.services.MessageService;
import kr.ssapi.triggers.TriggerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * SSAPI 관리 메인 메뉴.
 *
 * <p>3행 인벤토리 — 11=킷 / 13=트리거 / 15=설정 / 22=미션 (대시보드 안내).
 * 나머지는 회색 유리판 (클릭 cancel).
 */
public class MainGui implements GuiScreen {
    private final GuiManager gui;
    private final KitManager kits;
    private final TriggerRegistry triggers;
    private final MessageService messages;

    public MainGui(GuiManager gui, KitManager kits, TriggerRegistry triggers, MessageService messages) {
        this.gui = gui;
        this.kits = kits;
        this.triggers = triggers;
        this.messages = messages;
    }

    @Override public String name() { return "main"; }

    @Override
    public Inventory build(Player viewer, SsapiGuiHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27, messages.legacy("gui.main.title"));
        inv.setItem(11, GuiUtil.icon(Material.CHEST,
            messages.legacy("gui.main.kit_label"),
            messages.legacy("gui.main.kit_lore_count", "count", String.valueOf(kits.all().size())),
            messages.legacy("gui.main.kit_lore_hint")));
        inv.setItem(13, GuiUtil.icon(Material.PAPER,
            messages.legacy("gui.main.trigger_label"),
            messages.legacy("gui.main.trigger_lore_1"),
            messages.legacy("gui.main.trigger_lore_2")));
        inv.setItem(15, GuiUtil.icon(Material.COMPARATOR,
            messages.legacy("gui.main.settings_label"),
            messages.legacy("gui.main.settings_lore")));
        inv.setItem(22, GuiUtil.icon(Material.NETHER_STAR,
            messages.legacy("gui.main.mission_label"),
            messages.legacy("gui.main.mission_lore_1"),
            messages.legacy("gui.main.mission_lore_2")));
        GuiUtil.fillEmpty(inv);
        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, SsapiGuiHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        switch (event.getRawSlot()) {
            case 11:
                gui.open(p, new KitListGui(gui, kits, messages), null);
                break;
            case 13:
                gui.open(p, new TriggerListGui(gui, triggers, messages), TriggerRegistry.Scope.DONATION);
                break;
            case 15:
                gui.open(p, new SettingsGui(gui, messages), null);
                break;
            case 22:
                messages.send(p, "gui.main.chat_dashboard_link");
                p.closeInventory();
                break;
            default:
                break;
        }
    }
}
