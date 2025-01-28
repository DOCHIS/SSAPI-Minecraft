package kr.ssapi.actions;

import kr.ssapi.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RandomEffectAction implements DonationAction {
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
        boolean positiveEffects = plugin.getConfig().getBoolean("donation-actions.settings.random-effect.positive-effects", true);
        boolean negativeEffects = plugin.getConfig().getBoolean("donation-actions.settings.random-effect.negative-effects", true);

        List<PotionEffectType> availableEffects = new ArrayList<>();
        Map<PotionEffectType, String> effectNames = new HashMap<>();

        if (positiveEffects) {
            addPositiveEffects(effectNames);
            availableEffects.addAll(effectNames.keySet());
        }
        if (negativeEffects) {
            addNegativeEffects(effectNames);
            availableEffects.addAll(effectNames.keySet());
        }

        if (!availableEffects.isEmpty()) {
            List<String> effectDisplayNames = availableEffects.stream()
                .map(effectNames::get)
                .collect(Collectors.toList());

            showRandomSelectionTitle(player, effectDisplayNames, selectedObj -> {
                String selectedName = (String) selectedObj;
                PotionEffectType selectedEffect = availableEffects.stream()
                    .filter(effect -> effectNames.get(effect).equals(selectedName))
                    .findFirst()
                    .orElse(PotionEffectType.SPEED);

                player.addPotionEffect(new PotionEffect(selectedEffect, 200, 1));

                String effectMessage = config.getMessage("messages.donation.effect-applied")
                    .replace("{effect}", selectedName);
                if (!effectMessage.isEmpty()) {
                    player.sendMessage(effectMessage);
                }
            });
        }
    }

    private void addPositiveEffects(Map<PotionEffectType, String> effectNames) {
        Config config = Config.getInstance();
        for (PotionEffectType type : PotionEffectType.values()) {
            String name = config.getMessage("messages.actions.random-effect.effects.positive." + type.getName());
            if (!name.isEmpty()) {
                effectNames.put(type, name);
            }
        }
    }

    private void addNegativeEffects(Map<PotionEffectType, String> effectNames) {
        Config config = Config.getInstance();
        for (PotionEffectType type : PotionEffectType.values()) {
            String name = config.getMessage("messages.actions.random-effect.effects.negative." + type.getName());
            if (!name.isEmpty()) {
                effectNames.put(type, name);
            }
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
                String title = Config.getInstance().getMessage("messages.actions.random-effect.selection.title");
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
            String searchingTitle = Config.getInstance().getMessage("messages.actions.random-effect.selection.searching-title");
            player.sendTitle(searchingTitle, "§f" + randomOption.toString(), 0, 5, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            count.incrementAndGet();
        }, 0L, 2L));
    }
} 