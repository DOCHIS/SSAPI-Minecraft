package kr.ssapi.gui;

import kr.ssapi.services.MessageService;
import kr.ssapi.triggers.Trigger;
import kr.ssapi.triggers.TriggerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * 트리거 목록 GUI — scope 별 트리거를 나열하고 편집/삭제/토글 진입점을 제공.
 *
 * <p>하단 46~50번 슬롯에 scope 탭 버튼. 좌클릭=상세, Shift+클릭=토글, 우클릭=삭제 명령어 안내.
 */
public class TriggerListGui implements GuiScreen {
    private final GuiManager gui;
    private final TriggerRegistry registry;
    private final MessageService messages;

    public TriggerListGui(GuiManager gui, TriggerRegistry registry, MessageService messages) {
        this.gui = gui;
        this.registry = registry;
        this.messages = messages;
    }

    @Override public String name() { return "trigger_list"; }

    @Override
    public Inventory build(Player viewer, SsapiGuiHolder holder) {
        TriggerRegistry.Scope scope = (TriggerRegistry.Scope) holder.getContext();
        Inventory inv = Bukkit.createInventory(holder, 54,
            messages.legacy("gui.trigger_list.title", "scope", scopeLabel(scope)));

        List<Trigger> triggers = registry.get(scope);
        int slot = 0;
        for (Trigger t : triggers) {
            if (slot >= 45) break;
            Material mat = t.enabled ? Material.PAPER : Material.MAP;
            String label = messages.legacy(
                t.enabled ? "gui.trigger_list.item_label_enabled" : "gui.trigger_list.item_label_disabled",
                "id", t.id);
            inv.setItem(slot++, GuiUtil.icon(mat,
                label,
                messages.legacy("gui.trigger_list.item_priority", "priority", String.valueOf(t.priority)),
                messages.legacy("gui.trigger_list.item_match", "match", matchSummary(t)),
                messages.legacy("gui.trigger_list.item_stop", "stop", String.valueOf(t.stopOnMatch)),
                messages.legacy("gui.trigger_list.item_actions", "count", String.valueOf(t.actions.size())),
                "",
                messages.legacy("gui.trigger_list.item_left_click"),
                messages.legacy("gui.trigger_list.item_shift_click"),
                messages.legacy("gui.trigger_list.item_right_click")));
        }
        TriggerRegistry.Scope[] scopes = {
            TriggerRegistry.Scope.DONATION,
            TriggerRegistry.Scope.MISSION_RECEIVE,
            TriggerRegistry.Scope.MISSION_SETTLE,
            TriggerRegistry.Scope.MISSION_RESULT,
            TriggerRegistry.Scope.CONNECT
        };
        for (int i = 0; i < scopes.length; i++) {
            boolean current = scopes[i] == scope;
            String key = current ? "gui.trigger_list.scope_active" : "gui.trigger_list.scope_inactive";
            inv.setItem(46 + i, GuiUtil.icon(
                current ? Material.LIME_CONCRETE : Material.LIGHT_GRAY_CONCRETE,
                messages.legacy(key, "label", scopeLabel(scopes[i])),
                messages.legacy("gui.trigger_list.scope_lore")));
        }
        inv.setItem(45, GuiUtil.icon(Material.LIME_DYE,
            messages.legacy("gui.trigger_list.new_label"),
            messages.legacy("gui.trigger_list.new_lore")));
        inv.setItem(53, GuiUtil.backButton(messages));
        GuiUtil.fillEmpty(inv);
        return inv;
    }

    // scope enum 을 사람이 읽기 좋은 한국어 레이블로 변환
    private String scopeLabel(TriggerRegistry.Scope s) {
        switch (s) {
            case CONNECT:         return "연동 직후";
            case DONATION:        return "후원";
            case MISSION_RECEIVE: return "미션-받을때";
            case MISSION_SETTLE:  return "미션-정산";
            case MISSION_RESULT:  return "미션-결과";
            default: return s.name();
        }
    }

    // 트리거 match 조건을 한 줄 문자열로 요약
    private String matchSummary(Trigger t) {
        if (t.match == null) return "any";
        return t.match.toString();
    }

    @Override
    public void onClick(InventoryClickEvent event, SsapiGuiHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        TriggerRegistry.Scope scope = (TriggerRegistry.Scope) holder.getContext();
        int slot = event.getRawSlot();

        if (slot == 53) { p.closeInventory(); return; }
        if (slot == 45) {
            p.closeInventory();
            messages.send(p, "gui.trigger_list.chat_new_command", "scope", scope.name().toLowerCase());
            return;
        }
        TriggerRegistry.Scope[] scopes = {
            TriggerRegistry.Scope.DONATION,
            TriggerRegistry.Scope.MISSION_RECEIVE,
            TriggerRegistry.Scope.MISSION_SETTLE,
            TriggerRegistry.Scope.MISSION_RESULT,
            TriggerRegistry.Scope.CONNECT
        };
        if (slot >= 46 && slot < 46 + scopes.length) {
            gui.open(p, new TriggerListGui(gui, registry, messages), scopes[slot - 46]);
            return;
        }

        List<Trigger> triggers = registry.get(scope);
        if (slot < 0 || slot >= triggers.size()) return;
        Trigger target = triggers.get(slot);

        if (event.getClick() == ClickType.RIGHT) {
            p.closeInventory();
            messages.send(p, "gui.trigger_list.chat_delete_command",
                "scope", scope.name().toLowerCase(), "id", target.id);
            return;
        }
        if (event.getClick().isShiftClick()) {
            p.closeInventory();
            messages.send(p, "gui.trigger_list.chat_toggle_command",
                "scope", scope.name().toLowerCase(), "id", target.id);
            return;
        }
        gui.open(p, new TriggerEditorGui(gui, registry, messages),
            new Object[] { scope, target.id });
    }
}
