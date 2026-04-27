package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.SubCommand;
import kr.ssapi.events.MissionEvent;
import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /api테스트 — synthetic event 발행으로 실제 흐름과 동일 코드패스 통과.
 *
 * <p>지원:
 * <ul>
 *   <li>{@code /api테스트 <금액> [플레이어]} — 후원 synthetic
 *   <li>{@code /api테스트 미션 receive [금액] [플레이어]}
 *   <li>{@code /api테스트 미션 settle [총금액] [후원자수] [플레이어]}
 *   <li>{@code /api테스트 미션 result [플레이어]}
 * </ul>
 *
 * <p>모든 페이로드는 {@code _test: true} 마킹으로 운영 데이터와 분리.
 */
public class TestSub implements SubCommand {
    private final JavaPlugin plugin;
    private final MessageService messages;

    public TestSub(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override public String name() { return ""; }   // /api테스트 의 default sub
    @Override public List<String> aliases() { return Arrays.asList("donation"); }
    @Override public String permission() { return "ssapi.command.test"; }
    @Override public String shortDescription() { return "후원 / 미션 synthetic 이벤트 테스트"; }
    @Override public String usage() {
        return "/API테스트 <금액> [플레이어] | /API테스트 미션 <receive|settle|result> ...";
    }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        if (args.length < 1) return ExecutionResult.USAGE_ERROR;

        if ("미션".equalsIgnoreCase(args[0]) || "mission".equalsIgnoreCase(args[0])) {
            return executeMission(sender, args);
        }

        // 후원 synthetic
        long amount;
        try { amount = Long.parseLong(args[0]); }
        catch (NumberFormatException e) {
            messages.send(sender, "command.number_required", "field", "금액");
            return ExecutionResult.SUCCESS;
        }
        Player target = resolveTarget(sender, args.length >= 2 ? args[1] : null);
        if (target == null) {
            messages.send(sender, "command.player_not_found", "name",
                args.length >= 2 ? args[1] : "<자기자신>");
            return ExecutionResult.SUCCESS;
        }
        // donation synthetic — 실제 후원 흐름과 동일 코드패스 (DonationListener → TriggerMatcher → ActionChain)
        JSONObject data = new JSONObject();
        data.put("type", "donation");
        data.put("platform", "soop");
        data.put("streamer_id", target.getName());
        data.put("user_id", "test_donor_001");
        data.put("nickname", "테스트후원자");
        data.put("amount", amount);
        data.put("cnt", Math.max(1L, amount / 100L));
        data.put("message", "[테스트] " + sender.getName() + " 발사");
        data.put("_test", true);
        data.put("_test_player_uuid", target.getUniqueId().toString());
        Bukkit.getPluginManager().callEvent(new kr.ssapi.events.DonationEvent(data));

        messages.send(sender, "test_cmd.donation_simulated",
            "player", target.getName(), "amount", String.valueOf(amount));
        return ExecutionResult.SUCCESS;
    }

    private ExecutionResult executeMission(CommandSender sender, String[] args) {
        if (args.length < 2) return ExecutionResult.USAGE_ERROR;
        String phase = args[1].toLowerCase();
        switch (phase) {
            case "receive":
                return missionReceive(sender, args);
            case "settle":
                return missionSettle(sender, args);
            case "result":
                return missionResult(sender, args);
            default:
                return ExecutionResult.USAGE_ERROR;
        }
    }

    private ExecutionResult missionReceive(CommandSender sender, String[] args) {
        long amount = args.length >= 3 ? parseLong(args[2], 5000L) : 5000L;
        Player target = resolveTarget(sender, args.length >= 4 ? args[3] : null);
        if (target == null) target = (sender instanceof Player) ? (Player) sender : null;

        JSONObject data = baseMission(target, "CHALLENGE_GIFT", "receive", amount);
        data.put("nickname", "테스트후원자");
        data.put("user_id", "test_donor_001");
        Bukkit.getPluginManager().callEvent(new MissionEvent(data));
        messages.send(sender, "test_cmd.mission_receive", "amount", String.valueOf(amount));
        return ExecutionResult.SUCCESS;
    }

    private ExecutionResult missionSettle(CommandSender sender, String[] args) {
        long total = args.length >= 3 ? parseLong(args[2], 100000L) : 100000L;
        int donorCount = args.length >= 4 ? (int) parseLong(args[3], 5L) : 5;
        Player target = resolveTarget(sender, args.length >= 5 ? args[4] : null);
        if (target == null) target = (sender instanceof Player) ? (Player) sender : null;

        JSONObject data = baseMission(target, "CHALLENGE_SETTLE", "settle", total);
        JSONObject settle = new JSONObject();
        settle.put("total_amount", total);
        settle.put("total_cnt", donorCount * 10L);
        settle.put("donor_count", donorCount);
        JSONArray donors = new JSONArray();
        long perDonor = donorCount > 0 ? total / donorCount : total;
        for (int i = 0; i < donorCount; i++) {
            JSONObject d = new JSONObject();
            d.put("user_id", "test_donor_" + (i + 1));
            d.put("nickname", "테스트후원자" + (i + 1));
            d.put("amount", perDonor);
            d.put("cnt", 10);
            donors.put(d);
        }
        settle.put("donors", donors);
        data.put("settle", settle);
        Bukkit.getPluginManager().callEvent(new MissionEvent(data));
        messages.send(sender, "test_cmd.mission_settle",
            "total", String.valueOf(total), "donor_count", String.valueOf(donorCount));
        return ExecutionResult.SUCCESS;
    }

    private ExecutionResult missionResult(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args.length >= 3 ? args[2] : null);
        if (target == null) target = (sender instanceof Player) ? (Player) sender : null;

        JSONObject data = baseMission(target, "CHALLENGE_NOTICE", "result", 0L);
        JSONObject result = new JSONObject();
        result.put("mission_status", "SUCCESS");
        result.put("winner", target == null ? "" : target.getName());
        result.put("rank", 1);
        data.put("result", result);
        Bukkit.getPluginManager().callEvent(new MissionEvent(data));
        messages.send(sender, "test_cmd.mission_result");
        return ExecutionResult.SUCCESS;
    }

    private JSONObject baseMission(Player target, String missionType, String phase, long amount) {
        JSONObject data = new JSONObject();
        data.put("type", "mission");
        data.put("mission_type", missionType);
        data.put("mission_phase", phase);
        data.put("platform", "soop");
        data.put("streamer_id", target == null ? "test_streamer" : target.getName());
        data.put("title", "테스트 미션");
        data.put("key", "TEST-" + UUID.randomUUID().toString().substring(0, 8));
        data.put("amount", amount);
        data.put("cnt", 1);
        data.put("_test", true);
        if (target != null) data.put("_test_player_uuid", target.getUniqueId().toString());
        return data;
    }

    private Player resolveTarget(CommandSender sender, String name) {
        if (name != null) return Bukkit.getPlayerExact(name);
        return sender instanceof Player ? (Player) sender : null;
    }

    private long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return Arrays.asList("미션", "1100", "10000", "50000");
        if (args.length == 2 && args[0].equalsIgnoreCase("미션")) {
            return Arrays.asList("receive", "settle", "result");
        }
        if (args.length >= 2) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return out;
        }
        return new ArrayList<>();
    }
}
