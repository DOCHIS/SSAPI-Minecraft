package kr.ssapi.actions;

import kr.ssapi.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SpawnMobAction implements DonationAction {
    private JavaPlugin plugin;
    private final ConcurrentLinkedQueue<RandomSelectionTask> selectionQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);

    private class RandomSelectionTask {
        private final Player player;
        private final List<?> options;
        private final Consumer<Object> onComplete;

        public RandomSelectionTask(Player player, List<?> options, Consumer<Object> onComplete) {
            this.player = player;
            this.options = options;
            this.onComplete = onComplete;
        }
    }

    @Override
    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player) {
        Config config = Config.getInstance();
        boolean passiveMobs = plugin.getConfig().getBoolean("donation-actions.settings.spawn-mob.enabled.passive", true);
        boolean neutralMobs = plugin.getConfig().getBoolean("donation-actions.settings.spawn-mob.enabled.neutral", true);
        boolean hostileMobs = plugin.getConfig().getBoolean("donation-actions.settings.spawn-mob.enabled.hostile", true);
        boolean bossMobs = plugin.getConfig().getBoolean("donation-actions.settings.spawn-mob.enabled.boss", true);

        Map<String, List<EntityType>> mobCategories = new HashMap<>();
        Map<EntityType, String> mobNames = getMobNames();
        List<EntityType> availableMobs = new ArrayList<>();

        // 카테고리별로 몹 분류
        mobCategories.put("passive", getPassiveMobs());
        mobCategories.put("neutral", getNeutralMobs());
        mobCategories.put("hostile", getHostileMobs());
        mobCategories.put("boss", getBossMobs());

        // 활성화된 카테고리의 몹만 추가
        if (passiveMobs) availableMobs.addAll(mobCategories.get("passive"));
        if (neutralMobs) availableMobs.addAll(mobCategories.get("neutral"));
        if (hostileMobs) availableMobs.addAll(mobCategories.get("hostile"));
        if (bossMobs) availableMobs.addAll(mobCategories.get("boss"));

        if (!availableMobs.isEmpty()) {
            List<String> mobDisplayNames = availableMobs.stream()
                .map(mobNames::get)
                .collect(Collectors.toList());

            showRandomSelectionTitle(player, mobDisplayNames, selectedObj -> {
                String selectedName = (String) selectedObj;
                EntityType selectedType = availableMobs.stream()
                    .filter(type -> mobNames.get(type).equals(selectedName))
                    .findFirst()
                    .orElse(EntityType.PIG);

                Entity mob = player.getWorld().spawnEntity(player.getLocation(), selectedType);
                applyDifficulty(mob);
                playSpawnSound(player, selectedType, mobCategories);

                String spawnMessage = config.getMessage("messages.actions.spawn-mob.spawn-success")
                    .replace("{mob}", selectedName);
                if (!spawnMessage.isEmpty()) {
                    player.sendMessage(spawnMessage);
                }
            });
        } else {
            player.sendMessage(Config.getInstance().getPrefix() + "§c현재 소환 가능한 몹이 없습니다.");
        }
    }

    private Map<EntityType, String> getMobNames() {
        Map<EntityType, String> mobNames = new HashMap<>();
        Config config = Config.getInstance();
        
        // 비공격적 몹
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.passive." + type.name());
            if (!name.isEmpty()) {
                mobNames.put(type, name);
            }
        }
        
        // 중립적 몹
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.neutral." + type.name());
            if (!name.isEmpty()) {
                mobNames.put(type, name);
            }
        }
        
        // 적대적 몹
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.hostile." + type.name());
            if (!name.isEmpty()) {
                mobNames.put(type, name);
            }
        }
        
        // 보스 몹
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.boss." + type.name());
            if (!name.isEmpty()) {
                mobNames.put(type, name);
            }
        }
        
        return mobNames;
    }

    private void applyDifficulty(Entity mob) {
        if (mob instanceof org.bukkit.entity.LivingEntity) {
            org.bukkit.entity.LivingEntity livingMob = (org.bukkit.entity.LivingEntity) mob;
            int difficulty = plugin.getConfig().getInt("donation-actions.settings.spawn-mob.difficulty", 3);
            
            switch (difficulty) {
                case 5: // 매우 강함
                    livingMob.setMaxHealth(livingMob.getMaxHealth() * 3);
                    livingMob.setHealth(livingMob.getMaxHealth());
                    break;
                case 4: // 강함
                    livingMob.setMaxHealth(livingMob.getMaxHealth() * 2);
                    livingMob.setHealth(livingMob.getMaxHealth());
                    break;
                case 2: // 약함
                    livingMob.setMaxHealth(livingMob.getMaxHealth() * 0.5);
                    livingMob.setHealth(livingMob.getMaxHealth());
                    break;
                case 1: // 매우 약함
                    livingMob.setMaxHealth(livingMob.getMaxHealth() * 0.3);
                    livingMob.setHealth(livingMob.getMaxHealth());
                    break;
                default: // 보통 (3)
                    break;
            }
        }
    }

    private void playSpawnSound(Player player, EntityType selectedType, Map<String, List<EntityType>> mobCategories) {
        if (selectedType == EntityType.ENDER_DRAGON || selectedType == EntityType.WITHER) {
            // 보스 몹일 경우
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        } else if (mobCategories.get("hostile").contains(selectedType)) {
            // 적대적 몹일 경우
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 1.0f);
        } else {
            // 일반 몹일 경우
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void showRandomSelectionTitle(Player player, List<?> options, Consumer<Object> onComplete) {
        selectionQueue.offer(new RandomSelectionTask(player, options, onComplete));
        processNextInQueue();
    }

    private void processNextInQueue() {
        if (isProcessingQueue.get() || selectionQueue.isEmpty()) {
            return;
        }

        isProcessingQueue.set(true);
        RandomSelectionTask task = selectionQueue.poll();
        if (task != null) {
            executeRandomSelection(task.player, task.options, task.onComplete);
        }
    }

    private void executeRandomSelection(Player player, List<?> options, Consumer<Object> onComplete) {
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger taskId = new AtomicInteger();
        Random random = new Random();

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (count.get() >= 20) {
                Bukkit.getScheduler().cancelTask(taskId.get());
                Object selected = options.get(random.nextInt(options.size()));
                String title = Config.getInstance().getMessage("messages.actions.spawn-mob.selection.title");
                player.sendTitle(title, "§f" + selected.toString(), 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                onComplete.accept(selected);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    isProcessingQueue.set(false);
                    processNextInQueue();
                }, 60L);
                return;
            }

            Object randomOption = options.get(random.nextInt(options.size()));
            String searchingTitle = Config.getInstance().getMessage("messages.actions.spawn-mob.selection.searching-title");
            player.sendTitle(searchingTitle, "§f" + randomOption.toString(), 0, 5, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            count.incrementAndGet();
        }, 0L, 2L));
    }

    private List<EntityType> getPassiveMobs() {
        List<EntityType> passiveMobs = new ArrayList<>();
        Config config = Config.getInstance();
        
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.passive." + type.name());
            if (!name.isEmpty()) {
                passiveMobs.add(type);
            }
        }
        return passiveMobs;
    }

    private List<EntityType> getNeutralMobs() {
        List<EntityType> neutralMobs = new ArrayList<>();
        Config config = Config.getInstance();
        
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.neutral." + type.name());
            if (!name.isEmpty()) {
                neutralMobs.add(type);
            }
        }
        return neutralMobs;
    }

    private List<EntityType> getHostileMobs() {
        List<EntityType> hostileMobs = new ArrayList<>();
        Config config = Config.getInstance();
        
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.hostile." + type.name());
            if (!name.isEmpty()) {
                hostileMobs.add(type);
            }
        }
        return hostileMobs;
    }

    private List<EntityType> getBossMobs() {
        List<EntityType> bossMobs = new ArrayList<>();
        Config config = Config.getInstance();
        
        for (EntityType type : EntityType.values()) {
            String name = config.getMessage("messages.actions.spawn-mob.mobs.boss." + type.name());
            if (!name.isEmpty()) {
                bossMobs.add(type);
            }
        }
        return bossMobs;
    }
} 