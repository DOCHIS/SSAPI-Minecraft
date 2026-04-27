package kr.ssapi.gui;

import kr.ssapi.kits.Kit;
import kr.ssapi.kits.KitManager;
import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 킷 목록 GUI.
 */
public class KitListGui implements GuiScreen {
    private final GuiManager gui;
    private final KitManager kits;
    private final MessageService messages;
    private final List<String> kitOrder = new ArrayList<>();

    public KitListGui(GuiManager gui, KitManager kits, MessageService messages) {
        this.gui = gui;
        this.kits = kits;
        this.messages = messages;
    }

    @Override public String name() { return "kit_list"; }

    @Override
    public Inventory build(Player viewer, SsapiGuiHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 54, messages.legacy("gui.kit_list.title"));
        kitOrder.clear();
        kitOrder.addAll(kits.all().keySet());
        java.util.Collections.sort(kitOrder);

        int slot = 0;
        for (String name : kitOrder) {
            if (slot >= 45) break;
            Kit kit = kits.get(name);
            int itemCount = kit == null ? 0 : kit.getItems().size();
            inv.setItem(slot++, GuiUtil.icon(Material.CHEST,
                messages.legacy("gui.kit_list.item_label", "name", name),
                messages.legacy("gui.kit_list.item_display",
                    "display", kit == null ? name : kit.getDisplay()),
                messages.legacy("gui.kit_list.item_count", "count", String.valueOf(itemCount)),
                "",
                messages.legacy("gui.kit_list.item_left_click"),
                messages.legacy("gui.kit_list.item_right_click")));
        }
        inv.setItem(45, GuiUtil.icon(Material.LIME_DYE,
            messages.legacy("gui.kit_list.new_label"),
            messages.legacy("gui.kit_list.new_lore_1"),
            messages.legacy("gui.kit_list.new_lore_2")));
        inv.setItem(53, GuiUtil.backButton(messages));
        GuiUtil.fillEmpty(inv);
        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, SsapiGuiHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 53) {
            p.closeInventory();
            return;
        }
        if (slot == 45) {
            p.closeInventory();
            messages.send(p, "gui.kit_list.chat_new", "name", "<이름>");
            return;
        }
        if (slot < 0 || slot >= kitOrder.size()) return;

        String name = kitOrder.get(slot);
        if (name == null) return;
        if (event.getClick() == ClickType.RIGHT) {
            p.closeInventory();
            messages.send(p, "gui.kit_list.chat_delete_confirm", "name", name);
            return;
        }
        // 다른 OP 가 편집 중이면 락 획득 실패 메시지 출력
        if (!kits.acquireEditLock(p.getUniqueId(), name)) {
            messages.send(p, "gui.kit_list.chat_edit_locked");
            return;
        }
        gui.open(p, new KitEditorGui(gui, kits, messages), name);
    }
}
