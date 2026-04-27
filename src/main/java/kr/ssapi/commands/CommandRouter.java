package kr.ssapi.commands;

import kr.ssapi.services.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api / /api관리 / /api테스트 등 단일 루트의 서브커맨드 라우터.
 *
 * <p>각 SubCommand 의 권한·인자·결과를 통합 처리. SUCCESS 시 도움말 출력 안 함 (UX 개선).
 */
public class CommandRouter implements CommandExecutor, TabCompleter {

    private final String rootName;
    private final MessageService messages;
    private final Map<String, SubCommand> subs = new LinkedHashMap<>();

    public CommandRouter(String rootName, MessageService messages) {
        if (messages == null) throw new IllegalArgumentException("MessageService required");
        this.rootName = rootName;
        this.messages = messages;
    }

    // 서브커맨드를 이름과 별칭으로 등록 (메서드 체이닝 지원)
    public CommandRouter register(SubCommand sub) {
        subs.put(sub.name().toLowerCase(), sub);
        for (String alias : sub.aliases()) {
            subs.put(alias.toLowerCase(), sub);
        }
        return this;
    }

    public Map<String, SubCommand> uniqueSubs() {
        Map<String, SubCommand> uniq = new LinkedHashMap<>();
        for (SubCommand s : subs.values()) {
            uniq.putIfAbsent(s.name(), s);
        }
        return uniq;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            SubCommand defaultSub = subs.get("");
            if (defaultSub != null) {
                handleResult(sender, defaultSub, defaultSub.execute(sender, args));
            } else {
                sendHelp(sender, 1);
            }
            return true;
        }
        SubCommand sub = subs.get(args[0].toLowerCase());
        if (sub == null) {
            SubCommand defaultSub = subs.get("");
            if (defaultSub != null) {
                if (defaultSub.permission() != null && !sender.hasPermission(defaultSub.permission())) {
                    messages.send(sender, "permission.denied");
                    return true;
                }
                try {
                    handleResult(sender, defaultSub, defaultSub.execute(sender, args));
                } catch (Exception e) {
                    messages.send(sender, "command.generic_error", "error", e.getMessage());
                }
            } else {
                messages.send(sender, "command.unknown", "input", args[0]);
            }
            return true;
        }
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            messages.send(sender, "permission.denied");
            return true;
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        try {
            handleResult(sender, sub, sub.execute(sender, rest));
        } catch (Exception e) {
            messages.send(sender, "command.generic_error", "error", e.getMessage());
        }
        return true;
    }

    private void handleResult(CommandSender sender, SubCommand sub, SubCommand.ExecutionResult res) {
        if (res == SubCommand.ExecutionResult.USAGE_ERROR) {
            messages.send(sender, "command.usage_prefix", "usage", sub.usage());
        } else if (res == SubCommand.ExecutionResult.DENIED) {
            messages.send(sender, "permission.denied");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (Map.Entry<String, SubCommand> e : uniqueSubs().entrySet()) {
                if (e.getKey().toLowerCase().startsWith(prefix)) {
                    SubCommand s = e.getValue();
                    if (s.permission() == null || sender.hasPermission(s.permission())) {
                        out.add(e.getKey());
                    }
                }
            }
            return out;
        }
        SubCommand sub = subs.get(args[0].toLowerCase());
        if (sub == null) return new ArrayList<>();
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) return new ArrayList<>();
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return sub.tabComplete(sender, rest);
    }

    // 권한이 있는 서브커맨드만 필터링해 도움말 출력
    public void sendHelp(CommandSender sender, int page) {
        messages.send(sender, "command.help_header", "root", rootName);
        for (SubCommand s : uniqueSubs().values()) {
            if (s.permission() != null && !sender.hasPermission(s.permission())) continue;
            messages.send(sender, "command.help_entry", "usage", s.usage(), "desc", s.shortDescription());
        }
    }
}
