package kr.ssapi.actions;

import kr.ssapi.config.Config;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class InstantDeathAction implements DonationAction {
    private JavaPlugin plugin;
    private String donatorName;

    @Override
    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setDonatorName(String donatorName) {
        this.donatorName = donatorName;
    }

    @Override
    public void execute(Player player) {
        Config config = Config.getInstance();
        boolean protectInventory = config.isInstantDeathProtectInventory();

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);

        if (protectInventory) {
            PlayerInventory inventory = player.getInventory();
            ItemStack offHandItem = inventory.getItemInOffHand();
            boolean needsRestore = offHandItem != null && offHandItem.getType() != Material.AIR;
            
            // 왼손에 든 아이템이 있다면 백업
            ItemStack backupItem = needsRestore ? offHandItem.clone() : null;
            
            // 타이핑 효과와 함께 메시지 표시
            String title = config.getInstantDeathProtectTitle();
            int typingSpeed = config.getInstantDeathTypingSpeed();
            Sound typingSound = Sound.valueOf(config.getInstantDeathTypingSound());
            float volume = (float) config.getInstantDeathTypingSoundVolume();
            float pitch = (float) config.getInstantDeathTypingSoundPitch();
            
            new BukkitRunnable() {
                private int currentChar = 0;
                private final StringBuilder displayText = new StringBuilder();
                
                @Override
                public void run() {
                    if (currentChar >= title.length()) {
                        this.cancel();
                        // 타이핑이 끝나고 2초 후에 토템 지급 및 데미지
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            giveTotemsAndDamage(player, inventory, backupItem, needsRestore);
                        }, 40L); // 2초 = 40틱
                        return;
                    }
                    
                    displayText.append(title.charAt(currentChar));
                    player.sendTitle(displayText.toString(), "", 0, 20, 10);
                    player.playSound(player.getLocation(), typingSound, volume, pitch);
                    currentChar++;
                }
            }.runTaskTimer(plugin, 0L, typingSpeed);
        } else {
            // 인벤토리 보호가 꺼져있을 때는 일반적인 사망 처리
            player.setHealth(0);
        }

        // 브로드캐스트 설정이 켜져있을 때 메시지와 소리 전송
        if (config.isInstantDeathBroadcastEnabled()) {
            String broadcastMessage = config.getInstantDeathBroadcastMessage()
                    .replace("{player}", player.getName())
                    .replace("{donator}", donatorName);
            
            // 브로드캐스트 소리 설정
            Sound broadcastSound = Sound.valueOf(config.getInstantDeathBroadcastSound());
            float broadcastVolume = (float) config.getInstantDeathBroadcastSoundVolume();
            float broadcastPitch = (float) config.getInstantDeathBroadcastSoundPitch();
            
            // 모든 온라인 플레이어에게 메시지와 소리 전송
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                onlinePlayer.sendMessage(broadcastMessage);
                onlinePlayer.playSound(onlinePlayer.getLocation(), broadcastSound, broadcastVolume, broadcastPitch);
            }
        }

        String deathMessage = config.getMessage("messages.donation.death");
        if (!deathMessage.isEmpty()) {
            player.sendMessage(deathMessage);
        }
    }

    private void giveTotemsAndDamage(Player player, PlayerInventory inventory, ItemStack backupItem, boolean needsRestore) {
        // 시스템용 불사의 토템 생성
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        org.bukkit.inventory.meta.ItemMeta meta = totem.getItemMeta();
        meta.setDisplayName("§c[인벤세이브] §f불사의 토템");
        meta.setLore(java.util.Arrays.asList(
            "§7이 아이템은 시스템에서 자동으로 지급되는 아이템입니다.",
            "§7정상적인 경우 즉시 사라져야 하는 아이템입니다.",
            "§c만약 인벤토리에 남아있다면 운영자에게 문의해주세요."
        ));
        totem.setItemMeta(meta);

        // 토템 지급 및 데미지
        double damage = player.getMaxHealth() * 2;
        inventory.setItemInOffHand(totem);
        player.damage(damage);

        // 아이템 복구
        if (needsRestore) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack currentOffHandItem = inventory.getItemInOffHand();
                if (currentOffHandItem != null && currentOffHandItem.getType() == Material.TOTEM_OF_UNDYING) {
                    inventory.setItemInOffHand(null);
                }
                inventory.setItemInOffHand(backupItem);
            }, 1L);
        } else {
            // 복원할 아이템이 없는 경우 AIR로 설정
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack currentOffHandItem = inventory.getItemInOffHand();
                if (currentOffHandItem != null && currentOffHandItem.getType() == Material.TOTEM_OF_UNDYING) {
                    inventory.setItemInOffHand(new ItemStack(Material.AIR));
                }
            }, 1L);
        }
    }
} 