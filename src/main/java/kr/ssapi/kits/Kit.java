package kr.ssapi.kits;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 킷 데이터 모델 — 슬롯 번호와 ItemStack 의 매핑을 보관.
 *
 * <p>인벤토리 ↔ Kit 간 직렬화/역직렬화는 fillInventory / readFromInventory 가 담당.
 */
public class Kit {
    private final String name;
    private String display;
    private final Map<Integer, ItemStack> items;

    public Kit(String name, String display) {
        this.name = name;
        this.display = display == null ? name : display;
        this.items = new HashMap<>();
    }

    public String getName() { return name; }
    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public Map<Integer, ItemStack> getItems() { return items; }

    public void setSlot(int slot, ItemStack item) {
        if (item == null) items.remove(slot);
        else items.put(slot, item.clone());
    }

    public ItemStack getSlot(int slot) {
        ItemStack i = items.get(slot);
        return i == null ? null : i.clone();
    }

    // 킷 아이템을 인벤토리에 배치 (슬롯 범위를 벗어나면 무시)
    public void fillInventory(Inventory inventory) {
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            int slot = e.getKey();
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, e.getValue().clone());
            }
        }
    }

    // 인벤토리의 아이템을 읽어 킷에 저장 (null/AIR 슬롯 제외)
    public void readFromInventory(Inventory inventory) {
        items.clear();
        for (int i = 0; i < inventory.getSize() && i < 54; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.put(i, item.clone());
            }
        }
    }
}
