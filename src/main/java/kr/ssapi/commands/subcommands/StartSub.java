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
 * 후원 보상 수신 시작 서브커맨드 (/api 시작 또는 /api관리 시작).
 *
 * <p>PRIMARY 연동 정보를 기반으로 API 서버에 room/user PUT 요청을 보내 보상 수신을 활성화.
 */
public class StartSub implements SubCommand {
    private final MessageService messages;
    private final ApiClient apiClient;
    private final ApiErrorMapper errorMapper;
    private final boolean adminMode;

    public StartSub(MessageService messages, ApiClient apiClient, ApiErrorMapper errorMapper, boolean adminMode) {
        this.messages = messages;
        this.apiClient = apiClient;
        this.errorMapper = errorMapper;
        this.adminMode = adminMode;
    }

    @Override public String name() { return "시작"; }
    @Override public List<String> aliases() { return Arrays.asList("start"); }
    @Override public String permission() { return adminMode ? "ssapi.command.start" : null; }
    @Override public String shortDescription() { return adminMode ? "다른 플레이어 보상 받기 시작" : "후원 보상 받기 시작"; }
    @Override public String usage() { return adminMode ? "/API관리 시작 <플레이어>" : "/API 시작"; }

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

        messages.send(sender, "api.connecting");
        JSONObject body = new JSONObject();
        body.put("platform", conn.get().getPlatform().toApiString());
        body.put("user", conn.get().getStreamerId());
        ApiResponse<JSONObject> response = apiClient.put("/room/user", body);
        if (!response.isSuccess()) {
            messages.send(sender, "api.connect_fail", "message", errorMapper.resolveReason(response));
            return ExecutionResult.SUCCESS;
        }

        messages.send(target, "api.start");
        if (adminMode) messages.send(sender, "admin.start", "player", target.getName());
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
