package kr.ssapi.actions;

import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 랜덤 포션 효과 액션 — 설정된 효과 목록 중 하나를 랜덤 선택해 플레이어에게 적용.
 *
 * <p>룰렛 애니메이션(20회 반복 뒤 결정) 처리. 동시에 여러 이벤트가 와도 큐로 직렬화.
 */
public class RandomEffectAction implements Action {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ConcurrentLinkedQueue<SelectionTask> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private static class SelectionTask {
        final Player player;
        final List<String> options;
        final java.util.function.Consumer<String> onComplete;
        SelectionTask(Player p, List<String> o, java.util.function.Consumer<String> c) {
            this.player = p; this.options = o; this.onComplete = c;
        }
    }

    public RandomEffectAction(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public void execute(ActionSpec spec, ActionContext context) {
        Player player = context.player;
        if (player == null || !player.isOnline()) return;

        boolean positiveEnabled = plugin.getConfig().getBoolean("actions.random_effect.positive_effects", true);
        boolean negativeEnabled = plugin.getConfig().getBoolean("actions.random_effect.negative_effects", true);
        int durationTicks = plugin.getConfig().getInt("actions.random_effect.duration_ticks", 200);
        int amplifier = plugin.getConfig().getInt("actions.random_effect.amplifier", 1);

        Map<String, PotionEffectType> nameToType = new HashMap<>();
        if (positiveEnabled) collectEffects("action.random_effect.effects.positive", nameToType);
        if (negativeEnabled) collectEffects("action.random_effect.effects.negative", nameToType);

        if (nameToType.isEmpty()) {
            messages.send(player, "action.random_effect.no_effect_available");
            return;
        }

        List<String> displayNames = new ArrayList<>(nameToType.keySet());
        enqueueSelection(player, displayNames, selectedName -> {
            PotionEffectType selectedType = nameToType.get(selectedName);
            if (selectedType == null) return;
            player.addPotionEffect(new PotionEffect(selectedType, durationTicks, amplifier));
            messages.send(player, "action.random_effect.applied",
                "effect", selectedName,
                "duration_ticks", String.valueOf(durationTicks),
                "amplifier", String.valueOf(amplifier));
        });
    }

    // messages.yml 의 특정 섹션 키를 읽어 표시명 → PotionEffectType 매핑 구성
    private void collectEffects(String key, Map<String, PotionEffectType> out) {
        ConfigurationSection section = messages.getSection(key);
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type == null) continue;
            String display = messages.legacy(key + "." + name);
            if (display.isEmpty()) continue;
            out.put(display, type);
        }
    }

    // 룰렛 작업을 큐에 추가하고 처리 중이 아니면 즉시 시작
    private void enqueueSelection(Player player, List<String> options, java.util.function.Consumer<String> onComplete) {
        queue.offer(new SelectionTask(player, options, onComplete));
        processNext();
    }

    private void processNext() {
        if (processing.get() || queue.isEmpty()) return;
        processing.set(true);
        SelectionTask task = queue.poll();
        if (task != null) runSelection(task);
    }

    // 2틱 간격으로 20회 임시 표시 후 최종 선택 확정, 다음 큐 항목 처리
    private void runSelection(SelectionTask task) {
        AtomicInteger count = new AtomicInteger(0);
        int[] taskId = new int[1];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Player player = task.player;
        boolean soundEnabled = plugin.getConfig().getBoolean("sounds.random_effect", true);

        if (player.isOnline() && soundEnabled) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }

        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                processing.set(false);
                processNext();
                return;
            }
            if (count.get() >= 20) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                String selected = task.options.get(random.nextInt(task.options.size()));
                String title = messages.legacy("action.random_effect.selection_title");
                player.sendTitle(title, selected, 10, 40, 10);
                if (soundEnabled) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                task.onComplete.accept(selected);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    processing.set(false);
                    processNext();
                }, 60L);
                return;
            }
            String randomOption = task.options.get(random.nextInt(task.options.size()));
            String searchingTitle = messages.legacy("action.random_effect.selection_searching");
            player.sendTitle(searchingTitle, randomOption, 0, 5, 0);
            if (soundEnabled) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            }
            count.incrementAndGet();
        }, 0L, 2L);
    }
}
