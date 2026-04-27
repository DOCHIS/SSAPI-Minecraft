package kr.ssapi.listeners;

import kr.ssapi.actions.ActionChain;
import kr.ssapi.actions.ActionContext;
import kr.ssapi.events.DonationEvent;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import kr.ssapi.services.MessageService;
import kr.ssapi.storage.StorageManager;
import kr.ssapi.triggers.Trigger;
import kr.ssapi.triggers.TriggerMatcher;
import kr.ssapi.triggers.TriggerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 후원 이벤트 리스너 — DonationEvent 수신 시 트리거 매칭 및 액션 실행.
 *
 * <p>streamer_id 로 연동된 플레이어를 찾고, 오프라인이면 설정에 따라 실행 여부를 결정.
 * 후원 알림 메시지, 로그 저장, 매칭된 트리거의 ActionChain 순으로 처리.
 */
public class DonationListener implements Listener {
    private final JavaPlugin plugin;
    private final TriggerRegistry registry;
    private final ActionChain actionChain;
    private final MessageService messages;

    public DonationListener(JavaPlugin plugin, TriggerRegistry registry, ActionChain actionChain, MessageService messages) {
        this.plugin = plugin;
        this.registry = registry;
        this.actionChain = actionChain;
        this.messages = messages;
    }

    @EventHandler
    public void onDonation(DonationEvent event) {
        JSONObject data = event.getDonationData();
        if (data == null) return;

        String streamerId = data.optString("streamer_id", "");
        String platform   = data.optString("platform", "");
        long amount       = data.optLong("amount", 0);
        long cnt          = data.optLong("cnt", 0);
        String donator    = data.optString("nickname", "");

        Player player = resolveTestPlayer(data).orElseGet(() -> resolvePlayer(streamerId, platform));
        boolean executeWhenOffline = plugin.getConfig().getBoolean("reward.execute_when_offline", false);

        try {
            ApiConnection connection = streamerId.isEmpty() ? null
                : StorageManager.getDriver()
                    .getConnectionByStreamerIdAndPlatform(streamerId, platformEnum(platform))
                    .orElse(null);
            ApiLog log = new ApiLog(
                null, null, null,
                streamerId,
                donator,
                (int) cnt,
                "donation",
                String.valueOf(amount),
                player == null ? ApiLog.IsRun.N : ApiLog.IsRun.Y,
                player == null ? null : player.getName(),
                connection == null ? null : connection.getUuid(),
                player == null ? null : player.getWorld().getName(),
                LocalDateTime.now()
            );
            boolean saveDonation = plugin.getConfig().getBoolean("logging.save.donation", true);
            boolean saveFailure = plugin.getConfig().getBoolean("logging.save.failure", true);
            if (saveDonation || (saveFailure && player == null)) {
                StorageManager.getDriver().saveApiLog(log);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DonationListener 로그 저장 실패", e);
        }

        if (player == null && !executeWhenOffline) return;

        if (player != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("donator", donator);
            ph.put("amount", String.valueOf(amount));
            ph.put("amount_formatted", NumberFormat.getInstance(Locale.KOREA).format(amount));
            ph.put("cnt", String.valueOf(cnt));
            messages.send(player, "donation.format", ph);
            if (plugin.getConfig().getBoolean("sounds.donation", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        ActionContext ctx = buildContext(data, player, amount, cnt, donator, streamerId, platform);
        for (Trigger t : TriggerMatcher.match(amount, registry.get(TriggerRegistry.Scope.DONATION))) {
            actionChain.execute(t.actions, ctx);
        }
    }

    // 후원 데이터로부터 액션 실행에 필요한 placeholder 맵을 구성
    private ActionContext buildContext(JSONObject data, Player player, long amount, long cnt,
                                       String donator, String streamerId, String platform) {
        ActionContext ctx = new ActionContext(player);
        if (player != null) {
            ctx.put("player", player.getName());
            ctx.put("uuid", player.getUniqueId().toString());
        }
        ctx.put("streamer_id", streamerId);
        ctx.put("platform", platform);
        ctx.put("platform_name", platformName(platform));
        ctx.put("amount", String.valueOf(amount));
        ctx.put("amount_formatted", NumberFormat.getInstance(Locale.KOREA).format(amount));
        ctx.put("cnt", String.valueOf(cnt));
        ctx.put("donator_name", donator);
        ctx.put("donator_id", data.optString("user_id", ""));
        ctx.put("message", data.optString("message", ""));
        ctx.put("donation_type", extractDonationType(data));
        ctx.put("timestamp", LocalDateTime.now().toString());
        return ctx;
    }

    // 플랫폼별 extras 에서 후원 타입 문자열 추출 (숲/치지직 분기)
    private String extractDonationType(JSONObject data) {
        JSONObject extras = data.optJSONObject("extras");
        if (extras == null) return "";
        JSONObject soop = extras.optJSONObject("soop");
        if (soop != null) return soop.optString("typeName", "");
        JSONObject chzzk = extras.optJSONObject("chzzk");
        if (chzzk != null) return chzzk.optString("donationType", "");
        return "";
    }

    // streamer_id + platform 으로 스토리지 조회 → 온라인 플레이어 반환 (없으면 null)
    private Player resolvePlayer(String streamerId, String platformStr) {
        if (streamerId == null || streamerId.isEmpty()) return null;
        ApiConnection.Platform platform = platformEnum(platformStr);
        if (platform == null) return null;
        try {
            Optional<ApiConnection> conn = StorageManager.getDriver()
                .getConnectionByStreamerIdAndPlatform(streamerId, platform);
            if (!conn.isPresent()) return null;
            return Bukkit.getPlayer(UUID.fromString(conn.get().getUuid()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DonationListener resolvePlayer 실패", e);
            return null;
        }
    }

    private Optional<Player> resolveTestPlayer(JSONObject data) {
        if (!data.optBoolean("_test", false)) return Optional.empty();
        String uuid = data.optString("_test_player_uuid", "");
        if (uuid.isEmpty()) return Optional.empty();
        try { return Optional.ofNullable(Bukkit.getPlayer(UUID.fromString(uuid))); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    private ApiConnection.Platform platformEnum(String platformStr) {
        if ("soop".equalsIgnoreCase(platformStr)) return ApiConnection.Platform.숲;
        if ("chzzk".equalsIgnoreCase(platformStr)) return ApiConnection.Platform.치지직;
        return null;
    }

    private String platformName(String platform) {
        if ("soop".equalsIgnoreCase(platform)) return "숲";
        if ("chzzk".equalsIgnoreCase(platform)) return "치지직";
        return platform == null ? "" : platform;
    }
}
