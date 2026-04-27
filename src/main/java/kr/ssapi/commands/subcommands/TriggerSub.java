package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.gui.GuiManager;
import kr.ssapi.gui.TriggerListGui;
import kr.ssapi.services.MessageService;
import kr.ssapi.triggers.Trigger;
import kr.ssapi.triggers.TriggerRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /api관리 트리거 <목록|토글> [scope] [id]
 *
 * <p>편집은 GUI 또는 직접 yml 편집 권장 — 채팅 인풋으로 모든 매칭/액션 표현은 무리.
 */
public class TriggerSub implements SubCommand {
    private final GuiManager gui;
    private final TriggerRegistry registry;
    private final MessageService messages;

    public TriggerSub(GuiManager gui, TriggerRegistry registry, MessageService messages) {
        this.gui = gui;
        this.registry = registry;
        this.messages = messages;
    }

    @Override public String name() { return "트리거"; }
    @Override public List<String> aliases() { return Arrays.asList("trigger"); }
    @Override public String permission() { return "ssapi.trigger.edit"; }
    @Override public String shortDescription() { return "트리거 관리 (GUI 권장)"; }
    @Override public String usage() { return "/API관리 트리거 <목록|토글> [scope] [id]"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (args.length < 1) return ExecutionResult.USAGE_ERROR;
        switch (args[0]) {
            case "목록":
            case "list":
                if (sender instanceof Player) {
                    gui.open((Player) sender, new TriggerListGui(gui, registry, messages),
                        TriggerRegistry.Scope.DONATION);
                    return ExecutionResult.SUCCESS;
                }
                for (TriggerRegistry.Scope s : TriggerRegistry.Scope.values()) {
                    messages.send(sender, "trigger.scope_label", "scope", s.name());
                    for (Trigger t : registry.get(s)) {
                        messages.send(sender, "trigger.scope_entry",
                            "id", t.id,
                            "priority", String.valueOf(t.priority),
                            "enabled", String.valueOf(t.enabled));
                    }
                }
                return ExecutionResult.SUCCESS;
            case "토글":
            case "toggle": {
                if (args.length < 3) return ExecutionResult.USAGE_ERROR;
                TriggerRegistry.Scope scope;
                try { scope = TriggerRegistry.Scope.valueOf(args[1].toUpperCase()); }
                catch (IllegalArgumentException e) {
                    messages.send(sender, "trigger.unknown_scope", "scope", args[1]);
                    return ExecutionResult.SUCCESS;
                }
                String id = args[2];
                List<Trigger> triggers = new ArrayList<>(registry.get(scope));
                boolean found = false;
                for (int i = 0; i < triggers.size(); i++) {
                    Trigger t = triggers.get(i);
                    if (id.equals(t.id)) {
                        triggers.set(i, new Trigger(t.id, !t.enabled, t.match, t.priority, t.stopOnMatch, t.actions));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    messages.send(sender, "trigger.not_found", "id", id);
                    return ExecutionResult.SUCCESS;
                }
                registry.set(scope, triggers);
                messages.send(sender, "trigger.toggled", "scope", scope.name(), "id", id);
                return ExecutionResult.SUCCESS;
            }
            default:
                return ExecutionResult.USAGE_ERROR;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("목록", "토글");
        }
        if (args.length == 2 && args[0].equals("토글")) {
            List<String> out = new ArrayList<>();
            for (TriggerRegistry.Scope s : TriggerRegistry.Scope.values()) out.add(s.name().toLowerCase());
            return out;
        }
        if (args.length == 3 && args[0].equals("토글")) {
            try {
                TriggerRegistry.Scope scope = TriggerRegistry.Scope.valueOf(args[1].toUpperCase());
                List<String> out = new ArrayList<>();
                for (Trigger t : registry.get(scope)) out.add(t.id);
                return out;
            } catch (IllegalArgumentException ignored) {}
        }
        return new ArrayList<>();
    }
}
