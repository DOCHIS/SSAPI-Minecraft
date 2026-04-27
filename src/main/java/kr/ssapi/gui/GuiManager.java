package kr.ssapi.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GUI 라이프사이클·이벤트 라우터.
 *
 * <p>InventoryClickEvent / InventoryCloseEvent 를 받아 SsapiGuiHolder 인지 확인 후
 * 해당 GuiScreen 에 dispatch.
 */
public class GuiManager implements Listener {
    private final JavaPlugin plugin;

    public GuiManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** GuiScreen 을 열어줌. holder/inventory 생성 후 player 에게 표시. */
    public void open(Player player, GuiScreen screen, Object context) {
        SsapiGuiHolder holder = new SsapiGuiHolder(screen, context);
        Inventory inv = screen.build(player, holder);
        if (inv == null) return;
        holder.setInventory(inv);
        // build() 에서 holder.setInventory 안 했을 경우 대비
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SsapiGuiHolder)) return;
        SsapiGuiHolder h = (SsapiGuiHolder) holder;
        h.getScreen().onClick(event, h);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SsapiGuiHolder)) return;
        SsapiGuiHolder h = (SsapiGuiHolder) holder;
        h.getScreen().onClose(event, h);
    }
}
