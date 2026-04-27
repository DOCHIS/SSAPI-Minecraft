package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.config.ConfigValidator;
import kr.ssapi.services.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * /api관리 리로드 — 설정 다시 불러오기. atomic — 검증 실패 시 기존 유지.
 */
public class ReloadSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Runnable onSuccess;   // 추가 reload 작업 (TriggerRegistry 등)

    public ReloadSub(JavaPlugin plugin, MessageService messages, Runnable onSuccess) {
        this.plugin = plugin;
        this.messages = messages;
        this.onSuccess = onSuccess;
    }

    @Override public String name() { return "리로드"; }
    @Override public List<String> aliases() { return Arrays.asList("reload"); }
    @Override public String permission() { return "ssapi.command.reload"; }
    @Override public String shortDescription() { return "설정 다시 불러오기"; }
    @Override public String usage() { return "/API관리 리로드"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        try {
            plugin.reloadConfig();
            ConfigValidator.Report report = ConfigValidator.validateConfig(plugin.getConfig());
            if (!report.ok()) {
                messages.send(sender, "reload.validation_failed",
                    "count", String.valueOf(report.issues.size()));
                for (ConfigValidator.Issue i : report.issues) {
                    messages.send(sender, "reload.validation_issue", "issue", i.toString());
                }
                messages.send(sender, "reload.fail", "error", "검증 실패");
                return ExecutionResult.SUCCESS;
            }
            messages.reload();
            if (onSuccess != null) onSuccess.run();
            messages.send(sender, "reload.success");
        } catch (Exception e) {
            messages.send(sender, "reload.fail", "error", e.getMessage());
            plugin.getLogger().warning("ReloadSub: " + e.getMessage());
        }
        return ExecutionResult.SUCCESS;
    }
}
