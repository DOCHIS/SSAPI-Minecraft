package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.services.ApiClient;
import kr.ssapi.services.ApiErrorMapper;
import kr.ssapi.services.MessageService;
import kr.ssapi.services.api.ApiResponse;
import kr.ssapi.storage.StorageDriver;
import kr.ssapi.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 동시송출 연동 해제 서브커맨드 (/api 동시송출해제 또는 /api관리 동시송출해제).
 *
 * <p>SIMULCAST 타입 연동을 API 서버와 스토리지에서 모두 제거.
 */
public class SimulcastDisconnectSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ApiClient apiClient;
    private final ApiErrorMapper errorMapper;
    private final boolean adminMode;

    public SimulcastDisconnectSub(JavaPlugin plugin, MessageService messages,
                                  ApiClient apiClient, ApiErrorMapper errorMapper, boolean adminMode) {
        this.plugin = plugin;
        this.messages = messages;
        this.apiClient = apiClient;
        this.errorMapper = errorMapper;
        this.adminMode = adminMode;
    }

    @Override public String name() { return "동시송출해제"; }
    @Override public List<String> aliases() { return Arrays.asList("simulcast-disconnect"); }
    @Override public String permission() { return adminMode ? "ssapi.command.connect" : null; }
    @Override public String shortDescription() { return "동시송출 연동 해제"; }
    @Override public String usage() {
        return adminMode
            ? "/API관리 동시송출해제 <플레이어>"
            : "/API 동시송출해제";
    }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        Player target;
        if (adminMode) {
            if (args.length < 1) return ExecutionResult.USAGE_ERROR;
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "command.player_not_found", "name", args[0]);
                return ExecutionResult.SUCCESS;
            }
        } else {
            if (!(sender instanceof Player)) {
                messages.send(sender, "command.player_only");
                return ExecutionResult.SUCCESS;
            }
            target = (Player) sender;
        }

        String targetUuid = target.getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
            () -> executeDisconnect(sender, targetUuid));
        return ExecutionResult.SUCCESS;
    }

    private void executeDisconnect(CommandSender sender, String targetUuid) {
        StorageDriver storage = StorageManager.getDriver();
        try {
            Optional<ApiConnection> simul = storage.getConnectionByUuidAndType(
                targetUuid, ApiConnection.ConnectionType.SIMULCAST);
            if (!simul.isPresent()) {
                sendSync(sender, "api.simulcast_disconnect_fail",
                    "message", "동시송출 연동이 없습니다");
                return;
            }

            JSONObject body = new JSONObject();
            body.put("platform", simul.get().getPlatform().toApiString());
            body.put("user", simul.get().getStreamerId());
            ApiResponse<JSONObject> response = apiClient.delete("/room/user", body);
            if (!response.isSuccess()) {
                String reason = errorMapper.resolveReason(response);
                sendSync(sender, "api.simulcast_disconnect_fail", "message", reason);
                return;
            }
            storage.deleteConnection(simul.get());

            sendSync(sender, "api.simulcast_disconnect_success");
        } catch (Exception e) {
            plugin.getLogger().warning("SimulcastDisconnectSub 실패: " + e.getMessage());
            sendSync(sender, "api.simulcast_disconnect_fail", "message", e.getMessage());
        }
    }

    private void sendSync(CommandSender sender, String key, String... replacements) {
        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, replacements));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (adminMode && args.length == 1) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
            }
            return out;
        }
        return Collections.emptyList();
    }
}
