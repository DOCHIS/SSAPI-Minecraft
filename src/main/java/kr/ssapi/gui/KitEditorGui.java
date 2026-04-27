package kr.ssapi.gui;

import kr.ssapi.kits.Kit;
import kr.ssapi.kits.KitManager;
import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * 킷 편집 GUI — 인벤토리 0~44번 슬롯에 아이템을 배치해 킷을 구성.
 *
 * <p>닫을 때(onClose) 자동 저장. 뒤로가기(45번) / 저장(53번) 버튼 제공.
 * 편집 종료 시 편집 락을 해제.
 */
public class KitEditorGui implements GuiScreen {
    private static final int EDIT_AREA_END = 45;

    private final GuiManager gui;
    private final KitManager kits;
    private final MessageService messages;

    public KitEditorGui(GuiManager gui, KitManager kits, MessageService messages) {
        this.gui = gui;
        this.kits = kits;
        this.messages = messages;
    }

    @Override public String name() { return "kit_editor"; }

    @Override
    public Inventory build(Player viewer, SsapiGuiHolder holder) {
        String kitName = (String) holder.getContext();
        Inventory inv = Bukkit.createInventory(holder, 54,
            messages.legacy("gui.kit_editor.title", "name", kitName));

        Kit kit = kits.get(kitName);
        if (kit == null) {
            kit = new Kit(kitName, kitName);
            kits.put(kit);
        }
        kit.fillInventory(inv);

        inv.setItem(45, GuiUtil.backButton(messages));
        inv.setItem(49, GuiUtil.icon(Material.NAME_TAG,
            messages.legacy("gui.kit_editor.rename_label"),
            messages.legacy("gui.kit_editor.rename_lore_current", "display", kit.getDisplay()),
            messages.legacy("gui.kit_editor.rename_lore_hint", "name", kitName)));
        inv.setItem(53, GuiUtil.saveButton(messages));
        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, SsapiGuiHolder holder) {
        int slot = event.getRawSlot();
        if (slot >= EDIT_AREA_END && slot < 54) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player p = (Player) event.getWhoClicked();
            if (slot == 45) {
                save(holder, event.getInventory());
                gui.open(p, new KitListGui(gui, kits, messages), null);
            } else if (slot == 53) {
                save(holder, event.getInventory());
                messages.send(p, "gui.kit_editor.chat_saved", "name", String.valueOf(holder.getContext()));
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event, SsapiGuiHolder holder) {
        save(holder, event.getInventory());
        if (event.getPlayer() instanceof Player) {
            kits.releaseEditLock(event.getPlayer().getUniqueId());
        }
    }

    // 현재 인벤토리의 편집 영역(0~44번)을 킷에 저장
    private void save(SsapiGuiHolder holder, Inventory inv) {
        String kitName = (String) holder.getContext();
        Kit kit = kits.get(kitName);
        if (kit == null) kit = new Kit(kitName, kitName);
        Inventory editArea = Bukkit.createInventory(null, 54);
        for (int i = 0; i < EDIT_AREA_END; i++) {
            editArea.setItem(i, inv.getItem(i));
        }
        kit.readFromInventory(editArea);
        kits.put(kit);
    }
}
