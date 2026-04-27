package kr.ssapi.actions;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 콘솔 명령어 액션. {@code lines:} 의 각 라인을 placeholder 치환 후 dispatch.
 *
 * <p>옵셔널 {@code delays_ticks:} 가 지정되면 각 라인 사이 지연. 미지정 시 즉시 순차 실행.
 *
 * <p>예:
 * <pre>{@code
 * - type: command
 *   lines:
 *     - "say {donator_name} 감사"
 *     - "give {player} diamond 1"
 *   delays_ticks: [0, 20]   # 0틱 후 첫 줄, 20틱(1초) 후 두번째 줄
 * }</pre>
 */
public class CommandAction implements Action {
    private final JavaPlugin plugin;

    public CommandAction(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(ActionSpec spec, ActionContext context) {
        List<String> lines = spec.lines;
        List<Long> delays = spec.delaysTicks;

        // 각 라인을 placeholder 치환 후 delay 에 따라 즉시 또는 지연 실행
        for (int i = 0; i < lines.size(); i++) {
            final String resolved = context.resolve(lines.get(i));
            long delay = (delays != null && i < delays.size() && delays.get(i) != null) ? delays.get(i) : 0L;

            if (delay <= 0L) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved), delay);
            }
        }
    }
}
