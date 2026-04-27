package kr.ssapi.commands.subcommands;

import kr.ssapi.commands.CommandRouter;
import kr.ssapi.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * /api관리 도움말 [페이지] — CommandRouter 의 도움말 출력.
 */
public class HelpSub implements SubCommand {
    private final CommandRouter router;

    public HelpSub(CommandRouter router) {
        this.router = router;
    }

    @Override public String name() { return "도움말"; }
    @Override public List<String> aliases() { return Arrays.asList("help", "?"); }
    @Override public String shortDescription() { return "도움말"; }
    @Override public String usage() { return "/API관리 도움말 [페이지]"; }

    @Override
    public ExecutionResult execute(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }
        router.sendHelp(sender, page);
        return ExecutionResult.SUCCESS;
    }
}
