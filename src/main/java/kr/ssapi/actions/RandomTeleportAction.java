package kr.ssapi.actions;

import kr.ssapi.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class RandomTeleportAction implements DonationAction {
    private JavaPlugin plugin;

    @Override
    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player) {
        Config config = Config.getInstance();
        
        String titleMsg = config.getMessage("messages.actions.random-teleport.teleporting-title");
        String subtitleMsg = config.getMessage("messages.actions.random-teleport.teleporting-subtitle");
        if (!titleMsg.isEmpty() && !subtitleMsg.isEmpty()) {
            player.sendTitle(titleMsg, subtitleMsg, 10, 40, 10);
        }
        
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location safeLoc = findSafeLocation(player, config);
            if (safeLoc != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(safeLoc);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    
                    String successMsg = config.getMessage("messages.actions.random-teleport.safe-location-found");
                    if (!successMsg.isEmpty()) {
                        player.sendMessage(successMsg);
                    }
                });
            }
        });
    }

    private Location findSafeLocation(Player player, Config config) {
        final int maxAttempts = config.getInt("donation-actions.settings.random-teleport.search.max-attempts", 15);
        final long retryDelay = config.getLong("donation-actions.settings.random-teleport.search.retry-delay", 2000);
        int attempts = maxAttempts;
        Location playerLoc = player.getLocation();
        Location foundLocation = null;
        
        while (attempts > 0 && foundLocation == null) {
            // X, Z 좌표 결정
            int xDistance = generateRandomDistance(
                config.getTeleportXMinDistance(),
                config.getTeleportXMaxDistance()
            );
            xDistance *= Math.random() < 0.5 ? -1 : 1;
            
            int zDistance = generateRandomDistance(
                config.getTeleportZMinDistance(),
                config.getTeleportZMaxDistance()
            );
            zDistance *= Math.random() < 0.5 ? -1 : 1;
            
            // 새로운 위치 계산 및 월드 경계 제한 적용
            int newX = Math.max(config.getTeleportXWorldBorderMin(),
                    Math.min(config.getTeleportXWorldBorderMax(),
                            playerLoc.getBlockX() + xDistance));
            int newZ = Math.max(config.getTeleportZWorldBorderMin(),
                    Math.min(config.getTeleportZWorldBorderMax(),
                            playerLoc.getBlockZ() + zDistance));

            // 청크 로드 확인 및 로드
            Location checkLoc = new Location(player.getWorld(), newX, 64, newZ);
            try {
                Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    if (!checkLoc.getChunk().isLoaded()) {
                        return checkLoc.getChunk().load(true);
                    }
                    return true;
                }).get();

                // Y축 탐색을 위한 범위 설정
                int startY = config.isTeleportYSearchBottomToTop() ? 
                    config.getTeleportYWorldBorderMin() : 
                    config.getTeleportYWorldBorderMax();
                int endY = config.isTeleportYSearchBottomToTop() ? 
                    config.getTeleportYWorldBorderMax() : 
                    config.getTeleportYWorldBorderMin();
                int step = config.isTeleportYSearchBottomToTop() ? 1 : -1;

                Location finalLoc = new Location(player.getWorld(), newX, startY, newZ);
                Location[] foundSafeLoc = {null};

                boolean isValid = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    for (int y = startY; config.isTeleportYSearchBottomToTop() ? y <= endY : y >= endY; y += step) {
                        finalLoc.setY(y);
                        if (isSafeLocation(finalLoc, config)) {
                            foundSafeLoc[0] = finalLoc.clone();
                            return true;
                        }
                    }
                    return false;
                }).get();

                if (isValid && foundSafeLoc[0] != null) {
                    foundLocation = foundSafeLoc[0].add(0, 1, 0);
                    break;
                } else {
                    String searchingMsg = config.getMessage("messages.actions.random-teleport.searching");
                    if (!searchingMsg.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> 
                            player.sendMessage(searchingMsg));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            attempts--;
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (foundLocation == null) {
            String noSafeLocMsg = config.getMessage("messages.actions.random-teleport.no-safe-location");
            if (!noSafeLocMsg.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(noSafeLocMsg);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
            }
            return playerLoc;
        }

        return foundLocation;
    }

    private int generateRandomDistance(int min, int max) {
        return min + (int)(Math.random() * (max - min + 1));
    }

    private boolean isSafeLocation(Location loc, Config config) {
        Block current = loc.getBlock();
        Block above1 = loc.clone().add(0, 1, 0).getBlock();
        Block above2 = loc.clone().add(0, 2, 0).getBlock();
        
        // 현재 블록의 타입 체크
        Material type = current.getType();
        boolean isWater = type.toString().contains("WATER");
        boolean isLava = type.toString().contains("LAVA");
        boolean isSolid = type.isSolid();
        
        // 막힌 공간 허용이면 위 공간 체크 안함
        if (config.isTeleportAllowSolid()) {
            if (isSolid) return true;
        }
        
        // 위로 2칸이 막혀있으면 이동 불가 (막힌 공간 허용 아닐 때)
        if (!above1.getType().isAir() || !above2.getType().isAir()) {
            return false;
        }
        
        // 물/용암/고체 블록 체크
        if (isWater && config.isTeleportAllowWater()) {
            return true;
        }
        else if (isLava && config.isTeleportAllowLava()) {
            return true;
        }
        else if (isSolid) {
            return true;
        }
        
        return false;
    }
} 