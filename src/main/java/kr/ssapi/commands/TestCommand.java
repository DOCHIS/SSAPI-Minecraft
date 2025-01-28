package kr.ssapi.commands;

import kr.ssapi.actions.*;
import kr.ssapi.listeners.DonationListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestCommand implements CommandExecutor, TabCompleter {
    private final DonationListener donationListener;
    private final JavaPlugin plugin;
    private final List<String> actions = Arrays.asList(
        "GIVE_ITEM", "RANDOM_EFFECT", "SPAWN_MOB", "RANDOM_TELEPORT", "INSTANT_DEATH"
    );
    private final List<String> amounts = Arrays.asList(
        "1000", "2000", "3000", "5000", "10000"
    );

    public TestCommand(DonationListener donationListener, JavaPlugin plugin) {
        this.donationListener = donationListener;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (!sender.hasPermission("ssapi.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§c사용법: /API테스트 <금액/액션명> [플레이어]");
            return true;
        }

        String input = args[0];
        String targetPlayerName = args.length > 1 ? args[1] : null;
        Player targetPlayer = targetPlayerName != null ? 
            Bukkit.getPlayer(targetPlayerName) : (Player) sender;

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c대상 플레이어를 찾을 수 없습니다.");
            return true;
        }

        // 액션 직접 실행
        if (actions.contains(input.toUpperCase())) {
            DonationAction action = createAction(input.toUpperCase());
            if (action != null) {
                action.setPlugin(plugin);
                if (action instanceof InstantDeathAction) {
                    ((InstantDeathAction) action).setDonatorName(sender.getName() + "(테스트)");
                }
                action.execute(targetPlayer);
                sender.sendMessage("§a" + targetPlayer.getName() + "님에게 " + input + " 액션을 실행했습니다.");
            }
        } else {
            try {
                // 금액으로 실행
                int amount = Integer.parseInt(input);
                Map<String, Object> amounts = plugin.getConfig()
                    .getConfigurationSection("donation-actions.amounts")
                    .getValues(false);
                
                boolean found = false;
                for (Map.Entry<String, Object> entry : amounts.entrySet()) {
                    int requiredAmount = Integer.parseInt(entry.getKey());
                    if (amount == requiredAmount) {
                        String actionName = entry.getValue().toString();
                        DonationAction action = createAction(actionName);
                        if (action != null) {
                            action.setPlugin(plugin);
                            if (action instanceof InstantDeathAction) {
                                ((InstantDeathAction) action).setDonatorName(sender.getName() + "(테스트)");
                            }
                            action.execute(targetPlayer);
                            sender.sendMessage("§a" + targetPlayer.getName() + 
                                "님에게 " + amount + "원 후원에 해당하는 " + 
                                actionName + " 액션을 실행했습니다.");
                            found = true;
                        }
                        break;
                    }
                }
                
                if (!found) {
                    sender.sendMessage("§c해당 금액에 설정된 액션이 없습니다.");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c올바른 액션명 또는 금액을 입력해주세요.");
                sender.sendMessage("§7사용 가능한 액션: " + String.join(", ", actions));
            }
        }

        return true;
    }

    private DonationAction createAction(String actionName) {
        switch (actionName.toUpperCase()) {
            case "GIVE_ITEM":
                return new GiveItemAction();
            case "RANDOM_EFFECT":
                return new RandomEffectAction();
            case "SPAWN_MOB":
                return new SpawnMobAction();
            case "RANDOM_TELEPORT":
                return new RandomTeleportAction();
            case "INSTANT_DEATH":
                return new InstantDeathAction();
            default:
                return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 첫 번째 인자: 액션명 또는 금액
            String input = args[0].toUpperCase();
            completions.addAll(actions.stream()
                .filter(action -> action.startsWith(input))
                .collect(Collectors.toList()));
            completions.addAll(amounts.stream()
                .filter(amount -> amount.startsWith(args[0]))
                .collect(Collectors.toList()));
        } 
        else if (args.length == 2) {
            // 두 번째 인자: 플레이어 이름 (선택사항)
            String input = args[1].toLowerCase();
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList()));
        }
        
        return completions;
    }
} 