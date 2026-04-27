package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.services.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /api관리 디버그 <on|off> — logging.debug 토글.
 */
public class DebugSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;

    public DebugSub(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override public String name() { return "디버그"; }
    @Override public List<String> aliases() { return Arrays.asList("debug"); }
    @Override public String permission() { return "ssapi.command.debug"; }
    @Override public String shortDescription() { return "디버그 로깅 on/off"; }
    @Override public String usage() { return "/API관리 디버그 <on|off>"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (args.length < 1) return ExecutionResult.USAGE_ERROR;
        boolean on;
        if ("on".equalsIgnoreCase(args[0])) on = true;
        else if ("off".equalsIgnoreCase(args[0])) on = false;
        else return ExecutionResult.USAGE_ERROR;
        plugin.getConfig().set("logging.debug", on);
        plugin.saveConfig();
        messages.send(sender, on ? "debug.on" : "debug.off");
        return ExecutionResult.SUCCESS;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return Arrays.asList("on", "off");
        return Collections.emptyList();
    }
}
