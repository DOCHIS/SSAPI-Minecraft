package kr.ssapi.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * SSAPI GUI 임을 식별하는 holder. GuiManager 가 이걸로 라우팅.
 *
 * <p>Bukkit 의 InventoryHolder 패턴 — getHolder() 로 SsapiGuiHolder 인지 확인 후 dispatch.
 */
public class SsapiGuiHolder implements InventoryHolder {
    private final GuiScreen screen;
    private Inventory inventory;
    private final Object context;

    public SsapiGuiHolder(GuiScreen screen, Object context) {
        this.screen = screen;
        this.context = context;
    }

    public GuiScreen getScreen() { return screen; }
    public Object getContext() { return context; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
