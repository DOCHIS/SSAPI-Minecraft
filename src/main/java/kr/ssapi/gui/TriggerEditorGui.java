package kr.ssapi.gui;

import kr.ssapi.actions.ActionSpec;
import kr.ssapi.services.MessageService;
import kr.ssapi.triggers.Trigger;
import kr.ssapi.triggers.TriggerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 트리거 상세 편집 GUI — 선택된 트리거의 활성화/정지 등을 클릭으로 확인.
 *
 * <p>GUI 내에서 직접 수정은 불가. 채팅창 명령어 안내 메시지를 띄워 triggers.yml 편집을 유도.
 */
public class TriggerEditorGui implements GuiScreen {
    private final GuiManager gui;
    private final TriggerRegistry registry;
    private final MessageService messages;

    public TriggerEditorGui(GuiManager gui, TriggerRegistry registry, MessageService messages) {
        this.gui = gui;
        this.registry = registry;
        this.messages = messages;
    }

    @Override public String name() { return "trigger_editor"; }

    @Override
    public Inventory build(Player viewer, SsapiGuiHolder holder) {
        Object[] ctx = (Object[]) holder.getContext();
        TriggerRegistry.Scope scope = (TriggerRegistry.Scope) ctx[0];
        String triggerId = (String) ctx[1];

        Trigger t = findTrigger(scope, triggerId);
        Inventory inv = Bukkit.createInventory(holder, 27,
            messages.legacy("gui.trigger_editor.title", "id", triggerId));

        if (t == null) {
            inv.setItem(13, GuiUtil.icon(Material.BARRIER,
                messages.legacy("gui.trigger_editor.not_found")));
            GuiUtil.fillEmpty(inv);
            return inv;
        }

        inv.setItem(10, GuiUtil.icon(t.enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            messages.legacy(t.enabled ? "gui.trigger_editor.enabled" : "gui.trigger_editor.disabled"),
            messages.legacy("gui.trigger_editor.toggle_hint")));

        String matchStr = t.match == null ? "any" : t.match.toString();
        inv.setItem(12, GuiUtil.icon(Material.COMPARATOR,
            messages.legacy("gui.trigger_editor.match_label", "match", matchStr),
            messages.legacy("gui.trigger_editor.match_hint",
                "scope", scope.name().toLowerCase(), "id", triggerId)));

        inv.setItem(14, GuiUtil.icon(Material.REDSTONE_TORCH,
            messages.legacy("gui.trigger_editor.priority_label", "priority", String.valueOf(t.priority)),
            messages.legacy("gui.trigger_editor.priority_hint",
                "scope", scope.name().toLowerCase(), "id", triggerId)));

        inv.setItem(16, GuiUtil.icon(t.stopOnMatch ? Material.BARRIER : Material.GREEN_WOOL,
            messages.legacy("gui.trigger_editor.stop_label", "stop", String.valueOf(t.stopOnMatch)),
            messages.legacy("gui.trigger_editor.toggle_hint")));

        List<String> actionLore = new ArrayList<>();
        for (ActionSpec a : t.actions) {
            actionLore.add(messages.legacy("gui.trigger_editor.actions_lore_entry", "type", a.type));
        }
        inv.setItem(22, GuiUtil.icon(Material.WRITABLE_BOOK,
            messages.legacy("gui.trigger_editor.actions_label", "count", String.valueOf(t.actions.size())),
            actionLore));

        inv.setItem(18, GuiUtil.backButton(messages));
        GuiUtil.fillEmpty(inv);
        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, SsapiGuiHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        Object[] ctx = (Object[]) holder.getContext();
        TriggerRegistry.Scope scope = (TriggerRegistry.Scope) ctx[0];
        String triggerId = (String) ctx[1];

        switch (event.getRawSlot()) {
            case 18:
                gui.open(p, new TriggerListGui(gui, registry, messages), scope);
                break;
            case 10:
                p.closeInventory();
                messages.send(p, "gui.trigger_editor.chat_toggle_command",
                    "scope", scope.name().toLowerCase(), "id", triggerId);
                break;
            case 16:
                p.closeInventory();
                messages.send(p, "gui.trigger_editor.chat_stop_command",
                    "scope", scope.name().toLowerCase(), "id", triggerId);
                break;
            default:
                break;
        }
    }

    // scope 에 등록된 트리거 목록에서 id 로 검색
    private Trigger findTrigger(TriggerRegistry.Scope scope, String id) {
        for (Trigger t : registry.get(scope)) {
            if (id.equals(t.id)) return t;
        }
        return null;
    }
}
