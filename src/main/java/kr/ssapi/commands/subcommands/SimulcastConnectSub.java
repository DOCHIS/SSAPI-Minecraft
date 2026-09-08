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
 * 동시송출 추가 연동 서브커맨드 (/api 동시송출연동 또는 /api관리 동시송출연동).
 *
 * <p>PRIMARY 연동이 먼저 되어 있어야 하며, 기존 PRIMARY 와 다른 플랫폼으로만 등록 가능.
 */
public class SimulcastConnectSub implements SubCommand {
    private static final Pattern SOOP_ID = Pattern.compile("^[a-z0-9_-]+$");
    private static final Pattern CHZZK_ID = Pattern.compile("^[a-fA-F0-9]{12,32}$");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ApiClient apiClient;
    private final ApiErrorMapper errorMapper;
    private final boolean adminMode;

    public SimulcastConnectSub(JavaPlugin plugin, MessageService messages,
                               ApiClient apiClient, ApiErrorMapper errorMapper, boolean adminMode) {
        this.plugin = plugin;
        this.messages = messages;
        this.apiClient = apiClient;
        this.errorMapper = errorMapper;
        this.adminMode = adminMode;
    }

    @Override public String name() { return "동시송출연동"; }
    @Override public List<String> aliases() { return Arrays.asList("simulcast"); }
    @Override public String permission() { return adminMode ? "ssapi.command.connect" : null; }
    @Override public String shortDescription() { return "동시송출 추가 연동 (다른 플랫폼)"; }
    @Override public String usage() {
        return adminMode
            ? "/API관리 동시송출연동 <플레이어> <숲|치지직> <id>"
            : "/API 동시송출연동 <숲|치지직> <id>";
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

        String targetUuid = target.getUniqueId().toString();
        String targetName = target.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
            () -> executeConnect(sender, targetUuid, targetName, platform, channelId));
        return ExecutionResult.SUCCESS;
    }

    private void executeConnect(CommandSender sender, String targetUuid, String targetName,
                                ApiConnection.Platform platform, String channelId) {
        StorageDriver storage = StorageManager.getDriver();
        try {
            Optional<ApiConnection> primary = storage.getConnectionByUuidAndType(
                targetUuid, ApiConnection.ConnectionType.PRIMARY);
            if (!primary.isPresent()) {
                sendSync(sender, "api.connect.error.primary_required_first");
                return;
            }
            if (primary.get().getPlatform() == platform) {
                sendSync(sender, "api.connect.error.same_platform_as_primary");
                return;
            }

            JSONObject body = new JSONObject();
            body.put("platform", platform.toApiString());
            body.put("user", channelId);
            body.put("minecraft_uuid", targetUuid);
            ApiResponse<JSONObject> response = apiClient.put("/plugin/minecraft/user", body);
            if (!response.isSuccess()) {
                String reason = errorMapper.resolveReason(response);
                sendSync(sender, "api.simulcast_connect_fail", "message", reason);
                return;
            }

            String streamerName = response.data == null
                ? channelId
                : response.data.optString("streamer_name", channelId);
            ApiConnection conn = new ApiConnection(
                targetUuid,
                platform,
                channelId,
                streamerName,
                targetName,
                LocalDateTime.now(),
                ApiConnection.ConnectionType.SIMULCAST
            );
            storage.saveConnection(conn);

            sendSync(sender, "api.simulcast_connect_success",
                "platform_name", platform == ApiConnection.Platform.숲 ? "숲" : "치지직");
        } catch (Exception e) {
            plugin.getLogger().warning("SimulcastConnectSub 실패: " + e.getMessage());
            sendSync(sender, "api.simulcast_connect_fail", "message", e.getMessage());
        }
    }

    private void sendSync(CommandSender sender, String key, String... replacements) {
        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, replacements));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (adminMode) {
            if (args.length == 1) return online(args[0]);
            if (args.length == 2) return platforms(args[1]);
            if (args.length == 3) return idPlaceholder(args[1]);
            return Collections.emptyList();
        }
        if (args.length == 1) return platforms(args[0]);
        if (args.length == 2) return idPlaceholder(args[0]);
        return Collections.emptyList();
    }

    private List<String> online(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(prefix.toLowerCase())) out.add(p.getName());
        }
        return out;
    }

    private List<String> platforms(String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList("숲", "치지직")) if (s.startsWith(prefix)) out.add(s);
        return out;
    }

    private List<String> idPlaceholder(String platform) {
        if ("숲".equals(platform) || "soop".equalsIgnoreCase(platform)) {
            return Collections.singletonList("스트리머아이디 (마크아이디 아님!)");
        }
        if ("치지직".equals(platform) || "chzzk".equalsIgnoreCase(platform)) {
            return Collections.singletonList("치지직채널ID (마크아이디 아님!)");
        }
        return Collections.emptyList();
    }
}
