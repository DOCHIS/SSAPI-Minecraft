package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.services.ApiClient;
import kr.ssapi.services.ApiErrorMapper;
import kr.ssapi.services.MessageService;
import kr.ssapi.services.api.ApiResponse;
import kr.ssapi.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 후원 보상 수신 중지 서브커맨드 (/api 중지 또는 /api관리 중지).
 *
 * <p>PRIMARY 연동 정보를 기반으로 API 서버에 room/user DELETE 요청을 보내 보상 수신을 비활성화.
 */
public class StopSub implements SubCommand {
    private final MessageService messages;
    private final ApiClient apiClient;
    private final ApiErrorMapper errorMapper;
    private final boolean adminMode;

    public StopSub(MessageService messages, ApiClient apiClient, ApiErrorMapper errorMapper, boolean adminMode) {
        this.messages = messages;
        this.apiClient = apiClient;
        this.errorMapper = errorMapper;
        this.adminMode = adminMode;
    }

    @Override public String name() { return "중지"; }
    @Override public List<String> aliases() { return Arrays.asList("stop"); }
    @Override public String permission() { return adminMode ? "ssapi.command.stop" : null; }
    @Override public String shortDescription() { return adminMode ? "다른 플레이어 보상 받기 중지" : "후원 보상 받기 중지"; }
    @Override public String usage() { return adminMode ? "/API관리 중지 <플레이어>" : "/API 중지"; }

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

        Optional<ApiConnection> conn = StorageManager.getDriver().getConnectionByUuidAndType(
            target.getUniqueId().toString(), ApiConnection.ConnectionType.PRIMARY);
        if (!conn.isPresent()) {
            messages.send(sender, "api.not_connected");
            return ExecutionResult.SUCCESS;
        }

        messages.send(sender, "api.disconnecting");
        JSONObject body = new JSONObject();
        body.put("platform", conn.get().getPlatform().toApiString());
        body.put("user", conn.get().getStreamerId());
        ApiResponse<JSONObject> response = apiClient.delete("/room/user", body);
        if (!response.isSuccess()) {
            messages.send(sender, "api.disconnect_fail", "message", errorMapper.resolveReason(response));
            return ExecutionResult.SUCCESS;
        }

        messages.send(target, "api.stop");
        if (adminMode) messages.send(sender, "admin.stop", "player", target.getName());
        return ExecutionResult.SUCCESS;
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
