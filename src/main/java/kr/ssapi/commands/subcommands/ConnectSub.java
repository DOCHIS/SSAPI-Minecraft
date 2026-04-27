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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 스트리머 채널 연동 서브커맨드 (/api 연동 또는 /api관리 연동).
 *
 * <p>플랫폼(숲/치지직) 선택 후 채널 ID 검증 → API 호출 → 스토리지 저장.
 * adminMode=true 이면 대상 플레이어를 인자로 받고 권한 노드를 적용.
 */
public class ConnectSub implements SubCommand {
    private static final Pattern SOOP_ID = Pattern.compile("^[a-z0-9_-]+$");
    private static final Pattern CHZZK_ID = Pattern.compile("^[a-fA-F0-9]{12,32}$");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ApiClient apiClient;
    private final ApiErrorMapper errorMapper;
    private final boolean adminMode;

    public ConnectSub(JavaPlugin plugin, MessageService messages,
                      ApiClient apiClient, ApiErrorMapper errorMapper, boolean adminMode) {
        this.plugin = plugin;
        this.messages = messages;
        this.apiClient = apiClient;
        this.errorMapper = errorMapper;
        this.adminMode = adminMode;
    }

    @Override public String name() { return "연동"; }
    @Override public List<String> aliases() { return Arrays.asList("connect"); }
    @Override public String shortDescription() { return "스트리머 채널과 메인 연동"; }
    @Override public String usage() {
        return adminMode
            ? "/API관리 연동 <플레이어> <숲|치지직> <id>"
            : "/API 연동 <숲|치지직> <id>";
    }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        Player target;
        String[] connectArgs;
        if (adminMode) {
            if (args.length < 3) return ExecutionResult.USAGE_ERROR;
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "command.player_not_found", "name", args[0]);
                return ExecutionResult.SUCCESS;
            }
            connectArgs = Arrays.copyOfRange(args, 1, args.length);
        } else {
            if (!(sender instanceof Player)) {
                messages.send(sender, "command.player_only");
                return ExecutionResult.SUCCESS;
            }
            if (args.length < 2) return ExecutionResult.USAGE_ERROR;
            target = (Player) sender;
            connectArgs = args;
        }

        String platformInput = connectArgs[0];
        String channelId = connectArgs[1];
        ApiConnection.Platform platform;
        if ("숲".equals(platformInput) || "soop".equalsIgnoreCase(platformInput)) {
            platform = ApiConnection.Platform.숲;
            if (!SOOP_ID.matcher(channelId).matches()) {
                messages.send(sender, "api.connect.error.soop_id_invalid");
                return ExecutionResult.SUCCESS;
            }
        } else if ("치지직".equals(platformInput) || "chzzk".equalsIgnoreCase(platformInput)) {
            platform = ApiConnection.Platform.치지직;
            if (!CHZZK_ID.matcher(channelId).matches()) {
                messages.send(sender, "api.connect.error.chzzk_id_invalid");
                return ExecutionResult.SUCCESS;
            }
        } else {
            messages.send(sender, "api.connect.error.platform_invalid");
            return ExecutionResult.SUCCESS;
        }

        // 이미 PRIMARY 연동이 있는지 확인 후 API 호출 → 스토리지 저장
        StorageDriver storage = StorageManager.getDriver();
        try {
            Optional<ApiConnection> existing = storage.getConnectionByUuidAndType(
                target.getUniqueId().toString(), ApiConnection.ConnectionType.PRIMARY);
            if (existing.isPresent()) {
                messages.send(sender, "api.connect.error.streamer_already_registered");
                return ExecutionResult.SUCCESS;
            }

            JSONObject body = new JSONObject();
            body.put("platform", platform.toApiString());
            body.put("user", channelId);
            body.put("minecraft_uuid", target.getUniqueId().toString());
            ApiResponse<JSONObject> response = apiClient.put("/plugin/minecraft/user", body);
            if (!response.isSuccess()) {
                String reason = errorMapper.resolveReason(response);
                messages.send(sender, "api.connect_fail", "message", reason);
                return ExecutionResult.SUCCESS;
            }

            ApiConnection conn = new ApiConnection(
                target.getUniqueId().toString(),
                platform,
                channelId,
                target.getName(),
                target.getName(),
                LocalDateTime.now(),
                ApiConnection.ConnectionType.PRIMARY
            );
            storage.saveConnection(conn);

            messages.send(target, "api.connect_success");
            if (sender != target) {
                messages.send(sender, "admin.connect.success", "player", target.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("ConnectSub 실패: " + e.getMessage());
            messages.send(sender, "api.connect_fail", "message", e.getMessage());
        }
        return ExecutionResult.SUCCESS;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (adminMode) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            if (args.length == 2) return platformOptions(args[1]);
            return Collections.emptyList();
        }
        if (args.length == 1) return platformOptions(args[0]);
        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(prefix.toLowerCase())) out.add(p.getName());
        }
        return out;
    }

    private List<String> platformOptions(String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList("숲", "치지직")) {
            if (s.startsWith(prefix)) out.add(s);
        }
        return out;
    }
}
