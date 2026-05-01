package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.config.MissionSettings;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.services.MessageService;
import kr.ssapi.storage.StorageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * /api 상태 — 내 연동 상태
 * /api관리 상태 — 시스템 전체 상태
 */
public class StatusSub implements SubCommand {
    private final MessageService messages;
    private final boolean adminMode;

    public StatusSub(MessageService messages, boolean adminMode) {
        this.messages = messages;
        this.adminMode = adminMode;
    }

    @Override public String name() { return "상태"; }
    @Override public List<String> aliases() { return Arrays.asList("status"); }
    @Override public String permission() { return adminMode ? "ssapi.command.status" : null; }
    @Override public String shortDescription() { return adminMode ? "시스템 상태 조회" : "내 연동 상태 조회"; }
    @Override public String usage() { return adminMode ? "/API관리 상태" : "/API 상태"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (adminMode) {
            messages.send(sender, "status.header");
            MissionSettings.Snapshot s = MissionSettings.get();
            messages.send(sender, "status.mission_enabled", "value", String.valueOf(s.enabled));
            messages.send(sender, "status.mission_hook_timings", "value", String.valueOf(s.hookTimings));
            try {
                List<ApiConnection> all = StorageManager.getDriver().getAllConnections();
                messages.send(sender, "status.connections_count", "count", String.valueOf(all.size()));
            } catch (Exception e) {
                messages.send(sender, "status.storage_lookup_failed", "error", e.getMessage());
            }
            return ExecutionResult.SUCCESS;
        }
        if (!(sender instanceof Player)) {
            messages.send(sender, "command.player_only");
            return ExecutionResult.SUCCESS;
        }
        Player p = (Player) sender;
        try {
            List<ApiConnection> conns = StorageManager.getDriver().getConnectionsByUuid(p.getUniqueId().toString());
            if (conns.isEmpty()) {
                messages.send(sender, "api.not_connected");
                return ExecutionResult.SUCCESS;
            }
            messages.send(sender, "status.my_header");
            for (ApiConnection c : conns) {
                String pl = c.getPlatform() == ApiConnection.Platform.숲 ? "숲" : "치지직";
                String type = c.getConnectionType() == ApiConnection.ConnectionType.PRIMARY ? "메인" : "동시송출";
                String enabled = c.isEnabled() ? "켜짐" : "중지";
                messages.send(sender, "status.my_entry",
                    "type", type, "platform", pl, "streamer_id", c.getStreamerId(), "enabled", enabled);
            }
        } catch (Exception e) {
            messages.send(sender, "status.my_lookup_failed", "error", e.getMessage());
        }
        return ExecutionResult.SUCCESS;
    }
}
