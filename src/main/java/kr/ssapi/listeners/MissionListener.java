package kr.ssapi.listeners;

import kr.ssapi.actions.ActionChain;
import kr.ssapi.actions.ActionContext;
import kr.ssapi.config.MissionSettings;
import kr.ssapi.events.MissionEvent;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.services.FileLogService;
import kr.ssapi.storage.StorageManager;
import kr.ssapi.triggers.Trigger;
import kr.ssapi.triggers.TriggerMatcher;
import kr.ssapi.triggers.TriggerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 미션 이벤트 처리.
 *
 * <p>흐름:
 * <ol>
 *   <li>MissionEvent 수신
 *   <li>mission_phase 검사 + MissionSettings 의 hook_timings 게이트
 *   <li>streamer_id 로 ApiConnection 조회 → player UUID
 *   <li>scope 결정 (RECEIVE / SETTLE / RESULT)
 *   <li>SETTLE 일 때는 settle_payout (combined / individual) 분기:
 *     - combined: total_amount 로 트리거 1회
 *     - individual: donors[] 순회하며 각 후원자 amount 로 트리거 N회 (tick_spacing 간격)
 *   <li>매칭된 트리거의 ActionChain 실행 (placeholder 채워서)
 * </ol>
 */
public class MissionListener implements Listener {
    private final JavaPlugin plugin;
    private final TriggerRegistry registry;
    private final ActionChain actionChain;
    private final FileLogService fileLogs;

    public MissionListener(JavaPlugin plugin, TriggerRegistry registry, ActionChain actionChain, FileLogService fileLogs) {
        this.plugin = plugin;
        this.registry = registry;
        this.actionChain = actionChain;
        this.fileLogs = fileLogs;
    }

    @EventHandler
    public void onMission(MissionEvent event) {
        JSONObject data = event.getMissionData();
        if (data == null) return;

        String phase = event.getPhase();
        if (phase == null) {
            logMission(data, "", "skipped", "missing_phase", null);
            return;
        }

        // 게이트 — 서버측에서 이미 1차 게이트 통과했지만 클라이언트도 한번 더 검사
        MissionSettings.Snapshot s = MissionSettings.get();
        if (!s.isPhaseAllowed(phase)) {
            logMission(data, phase, "skipped", "phase_not_allowed", null);
            return;
        }

        Player player;
        String testUuid = data.optBoolean("_test", false) ? data.optString("_test_player_uuid", "") : "";
        if (!testUuid.isEmpty()) {
            try { player = Bukkit.getPlayer(UUID.fromString(testUuid)); }
            catch (IllegalArgumentException e) { player = null; }
        } else {
            player = resolvePlayer(data.optString("streamer_id", ""), data.optString("platform", ""));
        }
        if (!data.optBoolean("_test", false)
            && !isRewardEnabled(data.optString("streamer_id", ""), data.optString("platform", ""))) {
            logMission(data, phase, "skipped", "disabled", player);
            return;
        }
        boolean executeWhenOffline = plugin.getConfig().getBoolean("reward.execute_when_offline", false);
        if (player == null && !executeWhenOffline) {
            logMission(data, phase, "skipped", "player_offline", null);
            return;
        }

        TriggerRegistry.Scope scope = phaseToScope(phase);
        if (scope == null) {
            logMission(data, phase, "skipped", "unknown_phase", player);
            return;
        }

        switch (phase) {
            case "settle":
                handleSettle(data, player, scope);
                break;
            case "receive":
            case "result":
            default:
                handleSimple(data, player, scope);
                break;
        }
        logMission(data, phase, "processed", "", player);
    }

    private void logMission(JSONObject data, String phase, String outcome, String reason, Player player) {
        if (fileLogs == null) return;
        fileLogs.logMission(data, phase, outcome, reason,
            player == null ? null : player.getName(),
            player == null ? null : player.getUniqueId().toString());
    }

    // settle 페이로드 처리: combined 면 total_amount 로 1회, individual 이면 donors 순회
    private void handleSettle(JSONObject data, Player player, TriggerRegistry.Scope scope) {
        String payout = plugin.getConfig().getString("mission.settle_payout", "combined");
        JSONObject settle = data.optJSONObject("settle");
        if (settle == null) {
            plugin.getLogger().warning("settle 페이로드 누락 — mission_key=" + data.optString("key", ""));
            return;
        }

        boolean battle = "SETTLE".equalsIgnoreCase(data.optString("mission_type", ""));
        boolean roomGifts = battle && "room_gifts".equalsIgnoreCase(plugin.getConfig().getString(
            "mission.battle_settle_source", "settled"));
        // 대결미션의 실제 정산액 모드는 단일 권위값이므로 donor 개별 지급으로 바꾸지 않는다.
        if ("individual".equalsIgnoreCase(payout) && (!battle || roomGifts)) {
            JSONArray donors = settle.optJSONArray("donors");
            if (donors == null) return;
            int max = plugin.getConfig().getInt("mission.payout_safety.max_donors_processed", 200);
            int spacing = plugin.getConfig().getInt("mission.payout_safety.tick_spacing", 4);
            int count = Math.min(donors.length(), max);
            for (int i = 0; i < count; i++) {
                final JSONObject donor = donors.getJSONObject(i);
                long delay = (long) i * spacing;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    long amount = donor.optLong("amount", 0);
                    if (amount <= 0) return;
                    ActionContext ctx = buildContext(data, player, donor, amount);
                    fire(scope, amount, ctx);
                }, delay);
            }
        } else {
            // combined
            long total = roomGifts
                ? settle.optLong("room_total_amount", settle.optLong("total_amount", data.optLong("amount", 0)))
                : settle.optLong("total_amount", data.optLong("amount", 0));
            if (total <= 0) {
                logMission(data, "settle", "skipped", "non_positive_settlement", player);
                return;
            }
            JSONObject selectedData = roomGifts ? withSelectedBattleTotal(data, settle) : data;
            ActionContext ctx = buildContext(selectedData, player, null, total);
            fire(scope, total, ctx);
        }
    }

    private JSONObject withSelectedBattleTotal(JSONObject data, JSONObject settle) {
        JSONObject selectedData = new JSONObject(data.toString());
        JSONObject selectedSettle = new JSONObject(settle.toString());
        selectedSettle.put("total_cnt", settle.optLong("room_total_cnt", settle.optLong("total_cnt", 0)));
        selectedSettle.put("total_amount", settle.optLong("room_total_amount", settle.optLong("total_amount", 0)));
        selectedData.put("settle", selectedSettle);
        return selectedData;
    }

    // receive / result 처럼 단순 amount 기반으로 트리거 발화
    private void handleSimple(JSONObject data, Player player, TriggerRegistry.Scope scope) {
        long amount = data.optLong("amount", 0);
        ActionContext ctx = buildContext(data, player, null, amount);
        fire(scope, amount, ctx);
    }

    private void fire(TriggerRegistry.Scope scope, long amount, ActionContext ctx) {
        for (Trigger t : TriggerMatcher.match(amount, registry.get(scope))) {
            actionChain.execute(t.actions, ctx);
        }
    }

    // 미션 데이터로부터 액션 placeholder 를 구성 (settle/result 변수 포함)
    private ActionContext buildContext(JSONObject data, Player player, JSONObject donorOverride, long matchedAmount) {
        ActionContext ctx = new ActionContext(player);
        if (player != null) {
            ctx.put("player", player.getName());
            ctx.put("uuid", player.getUniqueId().toString());
        }
        ctx.put("streamer_id", data.optString("streamer_id", ""));
        ctx.put("platform", data.optString("platform", ""));
        ctx.put("platform_name", platformName(data.optString("platform", "")));
        ctx.put("mission_title", data.optString("title", ""));
        ctx.put("mission_key", data.optString("key", ""));
        ctx.put("mission_type", data.optString("mission_type", ""));
        ctx.put("mission_phase", data.optString("mission_phase", ""));
        ctx.put("mission_kind", missionKind(data));
        ctx.put("amount", String.valueOf(matchedAmount));
        ctx.put("amount_formatted", NumberFormat.getInstance(Locale.KOREA).format(matchedAmount));
        ctx.put("cnt", String.valueOf(data.optLong("cnt", 0)));

        // 후원자 정보 (donor 우선, 없으면 data 의 user_id/nickname)
        if (donorOverride != null) {
            ctx.put("donator_name", donorOverride.optString("nickname", ""));
            ctx.put("donator_id", donorOverride.optString("user_id", ""));
        } else {
            ctx.put("donator_name", data.optString("nickname", ""));
            ctx.put("donator_id", data.optString("user_id", ""));
        }

        // settle 변수
        JSONObject settle = data.optJSONObject("settle");
        if (settle != null) {
            long totalAmount = settle.optLong("total_amount", 0);
            ctx.put("total_amount", String.valueOf(totalAmount));
            ctx.put("total_amount_formatted", NumberFormat.getInstance(Locale.KOREA).format(totalAmount));
            ctx.put("total_cnt", String.valueOf(settle.optLong("total_cnt", 0)));
            ctx.put("donor_count", String.valueOf(settle.optLong("donor_count", 0)));
            JSONArray streamers = settle.optJSONArray("streamers");
            ctx.put("streamers", streamers == null ? "" : String.join(",", jsonArrayToStringList(streamers)));
            JSONArray donors = settle.optJSONArray("donors");
            if (donors != null && donors.length() > 0) {
                JSONObject top = findTopDonor(donors);
                if (top != null) {
                    ctx.put("top_donor_name", top.optString("nickname", ""));
                    ctx.put("top_donor_amount", String.valueOf(top.optLong("amount", 0)));
                }
                ctx.put("donors_summary", buildDonorsSummary(donors));
            }
        }

        // result 변수
        JSONObject result = data.optJSONObject("result");
        if (result != null) {
            ctx.put("mission_status", result.optString("mission_status", ""));
            ctx.put("winner", result.optString("winner", ""));
            ctx.put("loser", result.optString("loser", ""));
            ctx.put("rank", String.valueOf(result.optInt("rank", 0)));
            ctx.put("draw", String.valueOf(result.optBoolean("draw", false)));
            ctx.put("my_team_name", result.optString("my_team_name", ""));
        } else {
            // SOOP 의 NOTICE / CHALLENGE_NOTICE 는 extras 에 mission_status / winner 직접
            JSONObject extrasSoop = data.optJSONObject("extras");
            if (extrasSoop != null) extrasSoop = extrasSoop.optJSONObject("soop");
            if (extrasSoop != null) {
                ctx.put("mission_status", extrasSoop.optString("mission_status", ""));
                ctx.put("winner", extrasSoop.optString("winner", ""));
                ctx.put("loser", extrasSoop.optString("loser", ""));
                ctx.put("rank", String.valueOf(extrasSoop.optInt("rank", 0)));
                ctx.put("draw", String.valueOf(extrasSoop.optBoolean("draw", false)));
                ctx.put("my_team_name", extrasSoop.optString("my_team_name", ""));
            }
        }

        ctx.put("timestamp", java.time.LocalDateTime.now().toString());
        return ctx;
    }

    // mission_phase 문자열을 TriggerRegistry.Scope 로 변환
    private TriggerRegistry.Scope phaseToScope(String phase) {
        switch (phase) {
            case "receive": return TriggerRegistry.Scope.MISSION_RECEIVE;
            case "settle":  return TriggerRegistry.Scope.MISSION_SETTLE;
            case "result":  return TriggerRegistry.Scope.MISSION_RESULT;
            default: return null;
        }
    }

    private Player resolvePlayer(String streamerId, String platformStr) {
        if (streamerId == null || streamerId.isEmpty()) return null;
        ApiConnection.Platform platform;
        if ("soop".equalsIgnoreCase(platformStr)) platform = ApiConnection.Platform.숲;
        else if ("chzzk".equalsIgnoreCase(platformStr)) platform = ApiConnection.Platform.치지직;
        else return null;

        try {
            Optional<ApiConnection> conn = StorageManager.getDriver()
                .getConnectionByStreamerIdAndPlatform(streamerId, platform);
            if (!conn.isPresent()) return null;
            if (!conn.get().isEnabled()) return null;
            UUID uuid = UUID.fromString(conn.get().getUuid());
            return Bukkit.getPlayer(uuid);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "MissionListener resolvePlayer 실패", e);
            return null;
        }
    }

    private boolean isRewardEnabled(String streamerId, String platformStr) {
        if (streamerId == null || streamerId.isEmpty()) return true;
        ApiConnection.Platform platform;
        if ("soop".equalsIgnoreCase(platformStr)) platform = ApiConnection.Platform.숲;
        else if ("chzzk".equalsIgnoreCase(platformStr)) platform = ApiConnection.Platform.치지직;
        else return true;

        try {
            Optional<ApiConnection> conn = StorageManager.getDriver()
                .getConnectionByStreamerIdAndPlatform(streamerId, platform);
            return !conn.isPresent() || conn.get().isEnabled();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "MissionListener enabled 상태 조회 실패", e);
            return true;
        }
    }

    private String platformName(String platform) {
        if ("soop".equalsIgnoreCase(platform)) return "숲";
        if ("chzzk".equalsIgnoreCase(platform)) return "치지직";
        return platform == null ? "" : platform;
    }

    // mission_type 으로 사람이 읽기 좋은 미션 종류 (도전/참가/대결) 반환
    private String missionKind(JSONObject data) {
        String type = data.optString("mission_type", "");
        if (type.startsWith("CHALLENGE")) return "도전";
        if ("PARTICIPATION".equalsIgnoreCase(type) || "MISSION_PARTICIPATION".equalsIgnoreCase(type)) return "참가";
        return "대결";
    }

    // donors 배열에서 amount 가 가장 큰 후원자 객체를 반환
    private JSONObject findTopDonor(JSONArray donors) {
        JSONObject top = null;
        long max = -1;
        for (int i = 0; i < donors.length(); i++) {
            JSONObject d = donors.optJSONObject(i);
            if (d == null) continue;
            long amount = d.optLong("amount", 0);
            if (amount > max) { max = amount; top = d; }
        }
        return top;
    }

    // 상위 5명의 후원자를 "닉네임(금액원)" 형식으로 요약한 문자열 반환
    private String buildDonorsSummary(JSONArray donors) {
        StringBuilder sb = new StringBuilder();
        int max = Math.min(donors.length(), 5);
        for (int i = 0; i < max; i++) {
            JSONObject d = donors.optJSONObject(i);
            if (d == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.optString("nickname", "익명"))
              .append("(")
              .append(NumberFormat.getInstance(Locale.KOREA).format(d.optLong("amount", 0)))
              .append("원)");
        }
        if (donors.length() > max) sb.append(", ...");
        return sb.toString();
    }

    private java.util.List<String> jsonArrayToStringList(JSONArray arr) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null) out.add(s);
        }
        return out;
    }
}
