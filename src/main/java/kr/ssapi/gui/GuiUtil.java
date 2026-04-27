package kr.ssapi.gui;

import kr.ssapi.services.MessageService;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * GUI 공통 유틸리티 — 아이콘 생성, 빈 슬롯 채우기, 공통 버튼 생성.
 */
public final class GuiUtil {

    private GuiUtil() {}

    public static ItemStack icon(Material type, String name, String... lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack icon(Material type, String name, List<String> lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // 인벤토리의 비어있는 슬롯을 회색 유리판으로 채워 클릭을 차단
    public static void fillEmpty(Inventory inv) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    public static ItemStack backButton(MessageService messages) {
        return icon(Material.ARROW, messages.legacy("gui.common.back"));
    }

    public static ItemStack saveButton(MessageService messages) {
        return icon(Material.LIME_DYE, messages.legacy("gui.common.save"));
    }
}
