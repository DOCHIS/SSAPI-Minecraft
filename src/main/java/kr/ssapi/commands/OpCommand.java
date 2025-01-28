package kr.ssapi.commands;

import kr.ssapi.config.Config;
import kr.ssapi.utils.ConnectionUtil;
import kr.ssapi.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public class OpCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;

    public OpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("ssapi.admin")) {
            String message = Config.getInstance().getMessage("messages.admin.no-permission");
            if (!message.isEmpty()) sender.sendMessage(message);
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        if (args[0].equals("리로드")) {
            if (args.length != 1) {
                return true;
            }
            try {
                Config.getInstance().reload();
                String message = Config.getInstance().getMessage("messages.admin.reload-success");
                if (!message.isEmpty()) sender.sendMessage(message);
            } catch (Exception e) {
                String message = Config.getInstance().getMessage("messages.admin.reload-fail")
                    .replace("{message}", e.getMessage());
                if (!message.isEmpty()) sender.sendMessage(message);
                e.printStackTrace();
            }
            return true;
        }

        if (args.length < 2) {
            return true;
        }

        if (args[0].equals("아이템")) {
            if (args.length == 1) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                    return true;
                }
                return handleItemCommand((Player) sender, sender);
            }
            
            if (args.length == 2) {
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(Config.getInstance().getMessage("messages.admin.player-not-found"));
                    return true;
                }
                return handleItemCommand(targetPlayer, sender);
            }
        }

        if (args[0].equals("설정")) {
            if (args.length < 2) {
                return true;
            }

            switch (args[1].toLowerCase()) {
                case "초기화":
                    if (args.length != 2) {
                        return true;
                    }
                    return handleSettingReset(sender);
                
                case "추가":
                    if (args.length != 4) {
                        return true;
                    }
                    try {
                        int amount = Integer.parseInt(args[2]);
                        return handleSettingAdd(sender, amount, args[3].toUpperCase());
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c금액은 숫자여야 합니다.");
                        return true;
                    }
                
                case "삭제":
                    if (args.length != 3) {
                        return true;
                    }
                    try {
                        int amount = Integer.parseInt(args[2]);
                        return handleSettingRemove(sender, amount);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c금액은 숫자여야 합니다.");
                        return true;
                    }
                
                case "확인":
                    if (args.length != 2) {
                        return true;
                    }
                    return handleSettingList(sender);
                
                default:
                    return true;
            }
        }

        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.player-not-found"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "시작":
                if (args.length > 2) {
                    return true;
                }
                return ConnectionUtil.connectApi(targetPlayer, sender);
            case "중지":
                if (args.length > 2) {
                    return true;
                }
                return ConnectionUtil.disconnectApi(targetPlayer, sender);
            case "연동":
                if (args.length != 4) {
                    return true;
                }
                return ConnectionUtil.connect(targetPlayer, args[2], args[3], sender);
            default:
                return true;
        }
    }

    private void showUsage(CommandSender sender) {
        String message = Config.getInstance().getMessage("messages.admin.usage");
        if (!message.isEmpty()) sender.sendMessage(message);
    }

    private boolean handleItemCommand(Player player, CommandSender sender) {
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.item-empty")
                .replace("{player}", player.getName()));
            return true;
        }

        try {
            String itemData = ItemSerializer.serialize(player.getInventory().getItemInMainHand());
            plugin.getConfig().set("donation-actions.settings.give-item.item-data", itemData);
            plugin.saveConfig();
            Config.getInstance().reload();
            
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.item-set")
                .replace("{player}", player.getName()));
        } catch (Exception e) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.item-set-fail")
                .replace("{error}", e.getMessage()));
            e.printStackTrace();
        }
        return true;
    }

    private boolean handleSettingReset(CommandSender sender) {
        plugin.getConfig().set("donation-actions.amounts", null);
        plugin.saveConfig();
        Config.getInstance().reload();
        sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-reset"));
        return true;
    }

    private boolean handleSettingAdd(CommandSender sender, int amount, String action) {
        List<String> validActions = Arrays.asList(
            "GIVE_ITEM", "RANDOM_EFFECT", "SPAWN_MOB", 
            "RANDOM_TELEPORT", "INSTANT_DEATH"
        );
        
        if (!validActions.contains(action)) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.invalid-action")
                .replace("{actions}", String.join(", ", validActions)));
            return true;
        }

        String existingAction = plugin.getConfig().getString("donation-actions.amounts." + amount);
        if (action.equals(existingAction)) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-no-change")
                .replace("{amount}", String.valueOf(amount))
                .replace("{action}", action));
            return true;
        }

        boolean exists = existingAction != null;
        plugin.getConfig().set("donation-actions.amounts." + amount, action);
        plugin.saveConfig();
        Config.getInstance().reload();

        if (exists) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-update")
                .replace("{amount}", String.valueOf(amount))
                .replace("{action}", action));
        } else {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-add")
                .replace("{amount}", String.valueOf(amount))
                .replace("{action}", action));
        }
        return true;
    }

    private boolean handleSettingRemove(CommandSender sender, int amount) {
        if (!plugin.getConfig().contains("donation-actions.amounts." + amount)) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-not-found"));
            return true;
        }

        plugin.getConfig().set("donation-actions.amounts." + amount, null);
        plugin.saveConfig();
        Config.getInstance().reload();
        sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-remove")
            .replace("{amount}", String.valueOf(amount)));
        return true;
    }

    private boolean handleSettingList(CommandSender sender) {
        ConfigurationSection amounts = plugin.getConfig().getConfigurationSection("donation-actions.amounts");
        if (amounts == null || amounts.getKeys(false).isEmpty()) {
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-empty"));
            return true;
        }

        sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-list-header"));
        for (String amountStr : amounts.getKeys(false)) {
            String action = amounts.getString(amountStr);
            sender.sendMessage(Config.getInstance().getMessage("messages.admin.setting-list-format")
                .replace("{amount}", amountStr)
                .replace("{action}", action));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("ssapi.admin")) {
            return completions;
        }

        switch (args.length) {
            case 1:
                completions.addAll(Arrays.asList("리로드", "시작", "중지", "연동", "아이템", "설정"));
                break;
            case 2:
                switch (args[0].toLowerCase()) {
                    case "시작":
                    case "중지":
                    case "연동":
                    case "아이템":
                        return null; // 온라인 플레이어 목록 반환
                    case "설정":
                        completions.addAll(Arrays.asList("초기화", "추가", "삭제", "확인"));
                        break;
                }
                break;
            case 3:
                if (args[0].equals("연동")) {
                    completions.addAll(Arrays.asList("숲", "치지직"));
                } else if (args[0].equals("설정") && args[1].equals("추가") || args[1].equals("삭제")) {
                    completions.addAll(Arrays.asList("1000", "5000", "10000", "50000", "100000"));
                }
                break;
            case 4:
                if (args[0].equals("연동")) {
                    if (args[2].equals("숲")) {
                        completions.add("스트리머아이디");
                    } else if (args[2].equals("치지직")) {
                        completions.add("치지직채널ID");
                    }
                } else if (args[0].equals("설정") && args[1].equals("추가")) {
                    completions.addAll(Arrays.asList(
                        "GIVE_ITEM", "RANDOM_EFFECT", "SPAWN_MOB", 
                        "RANDOM_TELEPORT", "INSTANT_DEATH"
                    ));
                }
                break;
        }
        
        return completions;
    }
} 