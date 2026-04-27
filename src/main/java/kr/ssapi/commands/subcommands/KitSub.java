package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.gui.GuiManager;
import kr.ssapi.gui.KitEditorGui;
import kr.ssapi.gui.KitListGui;
import kr.ssapi.kits.Kit;
import kr.ssapi.kits.KitManager;
import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /api관리 킷 <목록|편집|지급|추가|삭제|이름> ...
 */
public class KitSub implements SubCommand {
    private final GuiManager gui;
    private final KitManager kits;
    private final MessageService messages;

    public KitSub(GuiManager gui, KitManager kits, MessageService messages) {
        this.gui = gui;
        this.kits = kits;
        this.messages = messages;
    }

    @Override public String name() { return "킷"; }
    @Override public List<String> aliases() { return Arrays.asList("kit"); }
    @Override public String permission() { return "ssapi.kit.edit"; }
    @Override public String shortDescription() { return "킷 관리"; }
    @Override public String usage() { return "/API관리 킷 <목록|편집|지급|추가|삭제|이름> ..."; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (args.length < 1) return ExecutionResult.USAGE_ERROR;
        String sub = args[0];
        switch (sub) {
            case "목록":
            case "list":
                if (!(sender instanceof Player)) {
                    messages.send(sender, "kit.list_chat", "list", String.valueOf(kits.all().keySet()));
                    return ExecutionResult.SUCCESS;
                }
                gui.open((Player) sender, new KitListGui(gui, kits, messages), null);
                return ExecutionResult.SUCCESS;
            case "편집":
            case "edit":
                if (args.length < 2) return ExecutionResult.USAGE_ERROR;
                if (!(sender instanceof Player)) {
                    messages.send(sender, "kit.edit_console_only");
                    return ExecutionResult.SUCCESS;
                }
                if (!kits.acquireEditLock(((Player) sender).getUniqueId(), args[1])) {
                    messages.send(sender, "kit.edit_locked");
                    return ExecutionResult.SUCCESS;
                }
                gui.open((Player) sender, new KitEditorGui(gui, kits, messages), args[1]);
                return ExecutionResult.SUCCESS;
            case "추가":
            case "add":
                if (args.length < 2) return ExecutionResult.USAGE_ERROR;
                kits.put(new Kit(args[1], args[1]));
                messages.send(sender, "kit.added", "name", args[1]);
                return ExecutionResult.SUCCESS;
            case "삭제":
            case "remove":
                if (args.length < 2) return ExecutionResult.USAGE_ERROR;
                kits.remove(args[1]);
                messages.send(sender, "kit.removed", "name", args[1]);
                return ExecutionResult.SUCCESS;
            case "이름":
            case "rename":
                if (args.length < 3) return ExecutionResult.USAGE_ERROR;
                Kit kit = kits.get(args[1]);
                if (kit == null) {
                    messages.send(sender, "kit.not_found", "name", args[1]);
                    return ExecutionResult.SUCCESS;
                }
                kit.setDisplay(args[2]);
                kits.put(kit);
                messages.send(sender, "kit.rename_done", "new", args[2]);
                return ExecutionResult.SUCCESS;
            case "지급":
            case "give":
                if (args.length < 3) return ExecutionResult.USAGE_ERROR;
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    messages.send(sender, "command.player_not_found", "name", args[1]);
                    return ExecutionResult.SUCCESS;
                }
                Kit gk = kits.get(args[2]);
                if (gk == null) {
                    messages.send(sender, "kit.not_found", "name", args[2]);
                    return ExecutionResult.SUCCESS;
                }
                for (java.util.Map.Entry<Integer, ItemStack> e : gk.getItems().entrySet()) {
                    java.util.Map<Integer, ItemStack> overflow = p.getInventory().addItem(e.getValue());
                    for (ItemStack o : overflow.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), o);
                    }
                }
                messages.send(sender, "kit.give_done", "kit", args[2], "player", p.getName());
                return ExecutionResult.SUCCESS;
            default:
                return ExecutionResult.USAGE_ERROR;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filtered(args[0], "목록", "편집", "지급", "추가", "삭제", "이름");
        }
        if (args.length == 2 && (args[0].equals("편집") || args[0].equals("삭제") || args[0].equals("이름"))) {
            return filtered(args[1], kits.all().keySet().toArray(new String[0]));
        }
        if (args.length == 2 && args[0].equals("지급")) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 3 && args[0].equals("지급")) {
            return filtered(args[2], kits.all().keySet().toArray(new String[0]));
        }
        return new ArrayList<>();
    }

    private List<String> filtered(String prefix, String... opts) {
        List<String> out = new ArrayList<>();
        String pl = prefix.toLowerCase();
        for (String s : opts) {
            if (s.toLowerCase().startsWith(pl)) out.add(s);
        }
        return out;
    }
}
