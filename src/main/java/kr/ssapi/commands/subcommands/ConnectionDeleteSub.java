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
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 강제 연동 정보 삭제 서브커맨드.
 *
 * <p>잘못 등록된 스트리머 ID 또는 마크 ID 기준으로 API 서버와 로컬 스토리지의 연동을 제거한다.
 */
public class ConnectionDeleteSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ApiClient apiClient;
    private final ApiErrorMapper errorMapper;

    public ConnectionDeleteSub(JavaPlugin plugin, MessageService messages,
                               ApiClient apiClient, ApiErrorMapper errorMapper) {
        this.plugin = plugin;
        this.messages = messages;
        this.apiClient = apiClient;
        this.errorMapper = errorMapper;
    }

    @Override public String name() { return "연동정보삭제"; }
    @Override public List<String> aliases() { return Arrays.asList("connection-delete", "연동삭제"); }
    @Override public String permission() { return "ssapi.command.connection_delete"; }
    @Override public String shortDescription() { return "스트리머/마크 아이디 기준 연동 정보 강제 삭제"; }
    @Override public String usage() { return "/API관리 연동정보삭제 <스트리머아이디|마크아이디>"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (args.length < 1 || args[0].trim().isEmpty()) return ExecutionResult.USAGE_ERROR;

        String target = args[0].trim();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> executeDelete(sender, target));
        return ExecutionResult.SUCCESS;
    }

    private void executeDelete(CommandSender sender, String target) {
        StorageDriver storage = StorageManager.getDriver();
        List<ApiConnection> matches = findMatches(storage.getAllConnections(), target);
        if (matches.isEmpty()) {
            sendSync(sender, "admin.connection_delete.not_found", "target", target);
            return;
        }

        int deleted = 0;
        List<String> failures = new ArrayList<>();
        for (ApiConnection connection : matches) {
            ApiResponse<JSONObject> response = deleteRemote(connection);
            if (response.isSuccess() || canDropLocalWhenRemoteMissing(response)) {
                storage.deleteConnection(connection);
                deleted++;
                continue;
            }

            String reason = errorMapper.resolveReason(response);
            failures.add(connectionLabel(connection) + " - " + reason);
        }

        if (failures.isEmpty()) {
            sendSync(sender, "admin.connection_delete.success",
                "target", target,
                "count", String.valueOf(deleted));
        } else if (deleted > 0) {
            sendSync(sender, "admin.connection_delete.partial_fail",
                "target", target,
                "success", String.valueOf(deleted),
                "failed", String.valueOf(failures.size()),
                "reason", failures.get(0));
            plugin.getLogger().warning("ConnectionDeleteSub 일부 실패: " + String.join("; ", failures));
        } else {
            sendSync(sender, "admin.connection_delete.fail",
                "target", target,
                "reason", failures.get(0));
            plugin.getLogger().warning("ConnectionDeleteSub 실패: " + String.join("; ", failures));
        }
    }

    private void sendSync(CommandSender sender, String key, String... replacements) {
        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, replacements));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return Collections.emptyList();

        String prefix = args[0].toLowerCase();
        Set<String> suggestions = new LinkedHashSet<>();
        for (ApiConnection connection : StorageManager.getDriver().getAllConnections()) {
            addIfMatches(suggestions, connection.getStreamerId(), prefix);
            addIfMatches(suggestions, connection.getName(), prefix);
        }

        List<String> out = new ArrayList<>(suggestions);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private List<ApiConnection> findMatches(List<ApiConnection> connections, String target) {
        List<ApiConnection> matches = new ArrayList<>();
        for (ApiConnection connection : connections) {
            if (equalsIgnoreCase(connection.getStreamerId(), target)
                || equalsIgnoreCase(connection.getName(), target)
                || equalsIgnoreCase(connection.getUuid(), target)) {
                matches.add(connection);
            }
        }
        return matches;
    }

    private ApiResponse<JSONObject> deleteRemote(ApiConnection connection) {
        JSONObject body = new JSONObject();
        body.put("platform", connection.getPlatform().toApiString());
        body.put("user", connection.getStreamerId());
        return apiClient.delete("/room/user", body);
    }

    private boolean canDropLocalWhenRemoteMissing(ApiResponse<JSONObject> response) {
        if (response.errorCode == null) return false;
        String code = response.errorCode.toLowerCase();
        return code.contains("not_registered") || code.contains("not_found");
    }

    private String connectionLabel(ApiConnection connection) {
        return connection.getPlatform().name() + "/" + connection.getStreamerId() + "/" + connection.getName();
    }

    private boolean equalsIgnoreCase(String value, String target) {
        return value != null && value.equalsIgnoreCase(target);
    }

    private void addIfMatches(Set<String> suggestions, String value, String prefix) {
        if (value == null || value.isEmpty()) return;
        if (value.toLowerCase().startsWith(prefix)) suggestions.add(value);
    }
}
