package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.kits.KitManager;
import kr.ssapi.services.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * /api관리 저장 — 메모리 상태를 디스크로 flush.
 */
public class SaveSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final KitManager kits;

    public SaveSub(JavaPlugin plugin, MessageService messages, KitManager kits) {
        this.plugin = plugin;
        this.messages = messages;
        this.kits = kits;
    }

    @Override public String name() { return "저장"; }
    @Override public List<String> aliases() { return Arrays.asList("save"); }
    @Override public String permission() { return "ssapi.command.save"; }
    @Override public String shortDescription() { return "메모리 상태 디스크 저장"; }
    @Override public String usage() { return "/API관리 저장"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        try {
            kits.saveAll();
            plugin.saveConfig();
            messages.send(sender, "save.success");
        } catch (Exception e) {
            messages.send(sender, "save.fail", "error", e.getMessage());
        }
        return ExecutionResult.SUCCESS;
    }
}
