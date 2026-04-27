package kr.ssapi.actions;

import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 랜덤 몹 소환 액션 — 설정된 카테고리(passive/neutral/hostile/boss)에서 몹을 하나 뽑아 소환.
 *
 * <p>룰렛 애니메이션 후 결정. difficulty 에 따라 체력 배율 적용.
 * RandomEffectAction 과 마찬가지로 큐 직렬화.
 */
public class SpawnMobAction implements Action {
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

    public SpawnMobAction(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public void execute(ActionSpec spec, ActionContext context) {
        Player player = context.player;
        if (player == null || !player.isOnline()) return;

        Map<String, EntityType> nameToType = new HashMap<>();
        Map<EntityType, String> typeToCategory = new HashMap<>();
        for (String category : new String[]{"passive", "neutral", "hostile", "boss"}) {
            if (!plugin.getConfig().getBoolean("actions.spawn_mob.enabled." + category, true)) continue;
            collectMobs("action.spawn_mob.mobs." + category, category, nameToType, typeToCategory);
        }

        if (nameToType.isEmpty()) {
            messages.send(player, "action.spawn_mob.no_mob_available");
            return;
        }

        List<String> displayNames = new ArrayList<>(nameToType.keySet());
        enqueueSelection(player, displayNames, selectedName -> {
            EntityType selectedType = nameToType.get(selectedName);
            if (selectedType == null) return;

            Entity mob = player.getWorld().spawnEntity(player.getLocation(), selectedType);
            applyDifficulty(mob);
            playSpawnSound(player, typeToCategory.getOrDefault(selectedType, "passive"));
            messages.send(player, "action.spawn_mob.success",
                "mob", selectedName,
                "category", typeToCategory.getOrDefault(selectedType, ""));
        });
    }

    // messages.yml 의 몹 섹션을 읽어 표시명 → EntityType + 카테고리 매핑 구성
    private void collectMobs(String key, String category, Map<String, EntityType> nameOut, Map<EntityType, String> categoryOut) {
        ConfigurationSection section = messages.getSection(key);
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            EntityType type;
            try { type = EntityType.valueOf(name); }
            catch (IllegalArgumentException e) { continue; }
            String display = messages.legacy(key + "." + name);
            if (display.isEmpty()) continue;
            nameOut.put(display, type);
            categoryOut.put(type, category);
        }
    }

    // difficulty 설정에 따라 소환된 몹의 최대 체력을 배율 조정
    private void applyDifficulty(Entity mob) {
        if (!(mob instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) mob;
        int difficulty = plugin.getConfig().getInt("actions.spawn_mob.difficulty", 3);
        double multiplier;
        switch (difficulty) {
            case 1: multiplier = 0.3; break;
            case 2: multiplier = 0.5; break;
            case 4: multiplier = 2.0; break;
            case 5: multiplier = 3.0; break;
            default: return;
        }
        living.setMaxHealth(living.getMaxHealth() * multiplier);
        living.setHealth(living.getMaxHealth());
    }

    // 카테고리별 소환 사운드 재생
    private void playSpawnSound(Player player, String category) {
        if (!plugin.getConfig().getBoolean("sounds.spawn_mob", true)) return;
        Sound sound;
        switch (category) {
            case "boss":    sound = Sound.ENTITY_WITHER_SPAWN; break;
            case "hostile": sound = Sound.ENTITY_ZOMBIE_AMBIENT; break;
            default:        sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP; break;
        }
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
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

    private void runSelection(SelectionTask task) {
        AtomicInteger count = new AtomicInteger(0);
        int[] taskId = new int[1];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Player player = task.player;
        boolean soundEnabled = plugin.getConfig().getBoolean("sounds.spawn_mob", true);

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
                String title = messages.legacy("action.spawn_mob.selection_title");
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
            String searchingTitle = messages.legacy("action.spawn_mob.selection_searching");
            player.sendTitle(searchingTitle, randomOption, 0, 5, 0);
            if (soundEnabled) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            }
            count.incrementAndGet();
        }, 0L, 2L);
    }
}
