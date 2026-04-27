package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.config.ConfigRepair;
import kr.ssapi.config.ConfigValidator;
import kr.ssapi.services.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /api관리 설정 <검증|복구|백업|원본>
 */
public class ConfigSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;

    public ConfigSub(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override public String name() { return "설정"; }
    @Override public List<String> aliases() { return Arrays.asList("config"); }
    @Override public String permission() { return "ssapi.config.write"; }
    @Override public String shortDescription() { return "설정 파일 관리"; }
    @Override public String usage() { return "/API관리 설정 <검증|복구|백업|원본>"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (args.length < 1) return ExecutionResult.USAGE_ERROR;
        switch (args[0]) {
            case "검증":
            case "validate": {
                ConfigValidator.Report r1 = ConfigValidator.validateConfig(plugin.getConfig());
                ConfigValidator.Report r2 = ConfigValidator.validateMissionTriggers(plugin.getConfig());
                File t = new File(plugin.getDataFolder(), "triggers.yml");
                ConfigValidator.Report r3 = t.exists()
                    ? ConfigValidator.validateTriggers(YamlConfiguration.loadConfiguration(t))
                    : new ConfigValidator.Report();
                ConfigValidator.Report all = new ConfigValidator.Report();
                all.merge(r1); all.merge(r2); all.merge(r3);
                if (all.ok()) {
                    messages.send(sender, "config_cmd.validate_ok");
                } else {
                    messages.send(sender, "config_cmd.validate_failed", "count", String.valueOf(all.issues.size()));
                    for (ConfigValidator.Issue i : all.issues) {
                        messages.send(sender, "config_cmd.validate_issue", "issue", i.toString());
                    }
                }
                return ExecutionResult.SUCCESS;
            }
            case "복구":
            case "repair": {
                if (args.length < 2) {
                    messages.send(sender, "config_cmd.repair_usage", "files", "config.yml|messages.yml|triggers.yml|kits.yml");
                    return ExecutionResult.SUCCESS;
                }
                ConfigRepair repair = new ConfigRepair(plugin);
                messages.send(sender, "config_cmd.repair_done", "message", repair.repair(args[1]));
                return ExecutionResult.SUCCESS;
            }
            case "백업":
            case "backup": {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                File backupDir = new File(plugin.getDataFolder(), "backups/" + stamp);
                backupDir.mkdirs();
                int count = 0;
                for (String f : Arrays.asList("config.yml", "messages.yml", "triggers.yml", "kits.yml")) {
                    File src = new File(plugin.getDataFolder(), f);
                    if (!src.exists()) continue;
                    try {
                        Path dst = backupDir.toPath().resolve(f);
                        Files.copy(src.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
                        count++;
                    } catch (Exception e) {
                        messages.send(sender, "config_cmd.backup_failed", "file", f, "error", e.getMessage());
                    }
                }
                messages.send(sender, "config_cmd.backup_done",
                    "count", String.valueOf(count), "dir", backupDir.getName());
                return ExecutionResult.SUCCESS;
            }
            case "원본":
            case "template": {
                if (args.length < 2) {
                    messages.send(sender, "config_cmd.template_usage", "files", "config.yml|messages.yml|triggers.yml|kits.yml");
                    return ExecutionResult.SUCCESS;
                }
                File out = new File(plugin.getDataFolder(), args[1] + ".template");
                ConfigRepair r = new ConfigRepair(plugin);
                messages.send(sender, "config_cmd.template_done", "message", r.extractTemplate(args[1], out));
                return ExecutionResult.SUCCESS;
            }
            default:
                return ExecutionResult.USAGE_ERROR;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return Arrays.asList("검증", "복구", "백업", "원본");
        if (args.length == 2 && (args[0].equals("복구") || args[0].equals("원본"))) {
            return Arrays.asList("config.yml", "messages.yml", "triggers.yml", "kits.yml");
        }
        return Collections.emptyList();
    }
}
