package kr.ssapi.commands;

import kr.ssapi.config.Config;
import kr.ssapi.storage.StorageDriver;
import kr.ssapi.storage.StorageManager;
import kr.ssapi.utils.ConnectionUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class ConnectCommand implements TabExecutor {
    private final StorageDriver storage;
    private final Config config;

    public ConnectCommand() {
        this.storage = StorageManager.getDriver();
        this.config = Config.getInstance();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if ("연동".startsWith(args[0].toLowerCase())) completions.add("연동");
            if ("시작".startsWith(args[0].toLowerCase())) completions.add("시작");
            if ("중지".startsWith(args[0].toLowerCase())) completions.add("중지");
        } else if (args.length == 2 && "연동".equals(args[0])) {
            if ("숲".startsWith(args[1].toLowerCase())) completions.add("숲");
            if ("치지직".startsWith(args[1].toLowerCase())) completions.add("치지직");
        } else if (args.length == 3 && "연동".equals(args[0])) {
            if ("숲".equals(args[1])) {
                completions.add("스트리머아이디 (마크아이디 아님!)");
            } else if ("치지직".equals(args[1])) {
                completions.add("치지직채널ID (마크아이디 아님!)");
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getMessage("messages.connect.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showFullUsage(sender);
            return true;
        }

        switch (args[0]) {
            case "연동":
                if (args.length < 2) {
                    return true;
                } else if (args.length == 2) {
                    if ("숲".equals(args[1]) || "치지직".equals(args[1])) {
                        String messageKey = "숲".equals(args[1]) ? 
                            "messages.connect.enter-soop-id" : 
                            "messages.connect.enter-chzzk-id";
                        sender.sendMessage(config.getMessage(messageKey));
                    }
                    return true;
                } else if (args.length == 3) {
                    return ConnectionUtil.connect(player, args[1], args[2], sender);
                } else {
                    return true;
                }
            case "시작":
                if (args.length != 1) {
                    return true;
                }
                return ConnectionUtil.connectApi(player, sender);
            case "중지":
                if (args.length != 1) {
                    return true;
                }
                return ConnectionUtil.disconnectApi(player, sender);
            default:
                return true;
        }
    }

    private void showFullUsage(CommandSender sender) {
        String message = config.getMessage("messages.connect.usage.full");
        if (!message.isEmpty()) sender.sendMessage(message);
    }

} 