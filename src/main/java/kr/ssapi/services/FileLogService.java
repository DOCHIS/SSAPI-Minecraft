package kr.ssapi.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import kr.ssapi.model.ApiLog;
import kr.ssapi.services.api.ApiResponse;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * plugins/SSApi/logs 하위에 운영 추적용 JSONL 로그를 저장한다.
 */
public class FileLogService {
    private static final int MAX_TEXT_LENGTH = 8_000;

    private final JavaPlugin plugin;
    private final Path logDir;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public FileLogService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder().toPath().resolve("logs");
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "logs 디렉토리 생성 실패", e);
        }
    }

    public void logApiCall(String method, String baseUrl, String path, JSONObject requestBody,
                           ApiResponse<?> response, long durationMs, String responseBody) {
        if (!isEnabled("api", true)) return;

        Map<String, Object> row = baseRow();
        row.put("method", method);
        row.put("url", baseUrl + path);
        row.put("path", path);
        row.put("duration_ms", durationMs);
        row.put("request", toMap(requestBody));
        row.put("http_status", response.httpStatus);
        row.put("success", response.isSuccess());
        row.put("reason", response.reason == null ? null : response.reason.name());
        row.put("error_code", response.errorCode);
        row.put("message", response.message);
        row.put("response", parseJsonOrText(responseBody));
        write("api-calls", row);
    }

    public void logDonation(JSONObject payload, ApiLog log, String outcome, String reason) {
        if (!isEnabled("donation", true)) return;

        Map<String, Object> row = baseRow();
        row.put("outcome", outcome);
        row.put("reason", reason);
        row.put("streamer_id", log == null ? payload.optString("streamer_id", "") : log.getStreamerId());
        row.put("platform", payload.optString("platform", ""));
        row.put("donator", log == null ? payload.optString("nickname", "") : log.getUsername());
        row.put("amount", payload.optLong("amount", 0));
        row.put("cnt", log == null ? payload.optLong("cnt", 0) : log.getCnt());
        row.put("player", log == null ? null : log.getPlayerName());
        row.put("player_uuid", log == null ? null : log.getPlayerUuid());
        row.put("payload", toMap(payload));
        write("donations", row);
    }

    public void logMission(JSONObject payload, String phase, String outcome, String reason,
                           String playerName, String playerUuid) {
        if (!isEnabled("mission", true)) return;

        Map<String, Object> row = baseRow();
        row.put("outcome", outcome);
        row.put("reason", reason);
        row.put("phase", phase);
        row.put("mission_key", payload.optString("key", ""));
        row.put("mission_title", payload.optString("title", ""));
        row.put("mission_type", payload.optString("mission_type", ""));
        row.put("streamer_id", payload.optString("streamer_id", ""));
        row.put("platform", payload.optString("platform", ""));
        row.put("amount", payload.optLong("amount", 0));
        row.put("player", playerName);
        row.put("player_uuid", playerUuid);
        row.put("payload", toMap(payload));
        write("missions", row);
    }

    private boolean isEnabled(String category, boolean defaultValue) {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) return false;
        return plugin.getConfig().getBoolean("logging.save." + category, defaultValue);
    }

    private Map<String, Object> baseRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", LocalDateTime.now().toString());
        row.put("server", plugin.getServer().getName());
        return row;
    }

    private Map<String, Object> toMap(JSONObject json) {
        return json == null ? null : json.toMap();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, MAX_TEXT_LENGTH) + "...(truncated)";
    }

    private Object parseJsonOrText(String text) {
        if (text == null || text.isBlank()) return text;
        String clipped = truncate(text);
        try {
            return JsonParser.parseString(clipped);
        } catch (JsonSyntaxException ignored) {
            return clipped;
        }
    }

    private synchronized void write(String prefix, Map<String, Object> row) {
        try {
            Files.createDirectories(logDir);
            Path file = logDir.resolve(prefix + "-" + LocalDate.now() + ".jsonl");
            Files.writeString(file, gson.toJson(row) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, prefix + " 파일 로그 저장 실패", e);
        }
    }
}
