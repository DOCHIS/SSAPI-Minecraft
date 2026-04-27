package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.config.MissionSettings;
import kr.ssapi.services.MessageService;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * /api관리 미션 — 현재 미션 설정 표시 (대시보드 안내).
 */
public class MissionSub implements SubCommand {
    private final MessageService messages;

    public MissionSub(MessageService messages) {
        this.messages = messages;
    }

    @Override public String name() { return "미션"; }
    @Override public List<String> aliases() { return Arrays.asList("mission"); }
    @Override public String permission() { return "ssapi.mission.settings"; }
    @Override public String shortDescription() { return "미션 설정 확인 (대시보드 링크)"; }
    @Override public String usage() { return "/API관리 미션"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        MissionSettings.Snapshot s = MissionSettings.get();
        messages.send(sender, "mission_admin.header");
        messages.send(sender, "mission_admin.enabled", "value", String.valueOf(s.enabled));
        messages.send(sender, "mission_admin.hook_timings", "value", String.valueOf(s.hookTimings));
        messages.send(sender, "mission_admin.dashboard_link");
        return ExecutionResult.SUCCESS;
    }
}
