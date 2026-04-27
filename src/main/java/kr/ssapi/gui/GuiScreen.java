package kr.ssapi.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Player;

/**
 * GUI 화면 인터페이스 — open/click/close 라이프사이클.
 *
 * <p>GuiManager 가 InventoryClickEvent / InventoryCloseEvent 를 받아 holder 의 screen 에게 dispatch.
 */
public interface GuiScreen {
    /** 화면 이름 (디버깅용) */
    String name();

    /** 새 인벤토리를 만들어 반환. holder.setInventory() 호출 필요. */
    Inventory build(Player viewer, SsapiGuiHolder holder);

    /** 클릭 이벤트 처리. cancel 여부는 핸들러가 직접 setCancelled */
    void onClick(InventoryClickEvent event, SsapiGuiHolder holder);

    /** 닫힐 때 호출 (저장 등) */
    default void onClose(InventoryCloseEvent event, SsapiGuiHolder holder) {}
}
