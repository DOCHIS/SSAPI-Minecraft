package kr.ssapi.actions;

import kr.ssapi.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 랜덤 텔레포트 액션 — 플레이어를 안전한 임의의 좌표로 이동.
 *
 * <p>비동기로 안전 위치를 탐색한 뒤 메인 스레드에서 텔레포트.
 * config.yml 의 range / safe_zone / search 설정으로 범위·조건을 제어.
 */
public class RandomTeleportAction implements Action {
    private final JavaPlugin plugin;
    private final MessageService messages;

    public RandomTeleportAction(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public void execute(ActionSpec spec, ActionContext context) {
        Player player = context.player;
        if (player == null || !player.isOnline()) return;

        String title = messages.legacy("action.random_teleport.title");
        String subtitle = messages.legacy("action.random_teleport.subtitle");
        if (!title.isEmpty() && !subtitle.isEmpty()) {
            player.sendTitle(title, subtitle, 10, 40, 10);
        }

        if (plugin.getConfig().getBoolean("sounds.random_teleport", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location safeLoc = findSafeLocation(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (safeLoc != null) {
                    player.teleport(safeLoc);
                    if (plugin.getConfig().getBoolean("sounds.random_teleport", true)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                    messages.send(player, "action.random_teleport.success",
                        "x", String.valueOf(safeLoc.getBlockX()),
                        "y", String.valueOf(safeLoc.getBlockY()),
                        "z", String.valueOf(safeLoc.getBlockZ()),
                        "world", safeLoc.getWorld().getName());
                } else {
                    messages.send(player, "action.random_teleport.no_safe_location");
                    if (plugin.getConfig().getBoolean("sounds.random_teleport", true)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                }
            });
        });
    }

    // 비동기 스레드에서 안전한 위치를 탐색 (최대 max_attempts 회 반복)
    private Location findSafeLocation(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        int maxAttempts = cfg.getInt("actions.random_teleport.search.max_attempts", 15);
        long retryDelay = cfg.getLong("actions.random_teleport.search.retry_delay_ms", 2000);

        int xMin = cfg.getInt("actions.random_teleport.range.x.distance_min", 3000);
        int xMax = cfg.getInt("actions.random_teleport.range.x.distance_max", 6000);
        int xWMin = cfg.getInt("actions.random_teleport.range.x.world_min", -30000);
        int xWMax = cfg.getInt("actions.random_teleport.range.x.world_max", 30000);
        int zMin = cfg.getInt("actions.random_teleport.range.z.distance_min", 3000);
        int zMax = cfg.getInt("actions.random_teleport.range.z.distance_max", 6000);
        int zWMin = cfg.getInt("actions.random_teleport.range.z.world_min", -30000);
        int zWMax = cfg.getInt("actions.random_teleport.range.z.world_max", 30000);
        int yWMin = cfg.getInt("actions.random_teleport.range.y.world_min", 0);
        int yWMax = cfg.getInt("actions.random_teleport.range.y.world_max", 256);
        boolean bottomToTop = "BOTTOM_TO_TOP".equalsIgnoreCase(
            cfg.getString("actions.random_teleport.range.y.search_direction", "BOTTOM_TO_TOP"));
        boolean allowWater = cfg.getBoolean("actions.random_teleport.safe_zone.allow_water", true);
        boolean allowLava = cfg.getBoolean("actions.random_teleport.safe_zone.allow_lava", false);
        boolean allowSolid = cfg.getBoolean("actions.random_teleport.safe_zone.allow_solid", false);

        Location playerLoc = player.getLocation();
        int attempts = maxAttempts;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        while (attempts > 0) {
            int xDist = (xMin + random.nextInt(xMax - xMin + 1)) * (random.nextBoolean() ? -1 : 1);
            int zDist = (zMin + random.nextInt(zMax - zMin + 1)) * (random.nextBoolean() ? -1 : 1);
            int newX = clamp(playerLoc.getBlockX() + xDist, xWMin, xWMax);
            int newZ = clamp(playerLoc.getBlockZ() + zDist, zWMin, zWMax);

            try {
                Location chunkLoc = new Location(player.getWorld(), newX, 64, newZ);
                Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    if (!chunkLoc.getChunk().isLoaded()) chunkLoc.getChunk().load(true);
                    return true;
                }).get();

                Location result = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    int startY = bottomToTop ? yWMin : yWMax;
                    int endY = bottomToTop ? yWMax : yWMin;
                    int step = bottomToTop ? 1 : -1;
                    Location loc = new Location(player.getWorld(), newX, startY, newZ);
                    for (int y = startY; bottomToTop ? y <= endY : y >= endY; y += step) {
                        loc.setY(y);
                        if (isSafeLocation(loc, allowWater, allowLava, allowSolid)) {
                            return loc.clone().add(0, 1, 0);
                        }
                    }
                    return null;
                }).get();

                if (result != null) return result;
                Bukkit.getScheduler().runTask(plugin,
                    () -> messages.send(player, "action.random_teleport.searching"));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "RandomTeleportAction 위치 탐색 오류", e);
            }

            attempts--;
            try { Thread.sleep(retryDelay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return null;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // 지면 블록과 위 2칸이 모두 안전한지 검사
    private boolean isSafeLocation(Location loc, boolean allowWater, boolean allowLava, boolean allowSolid) {
        Block current = loc.getBlock();
        Block above1 = loc.clone().add(0, 1, 0).getBlock();
        Block above2 = loc.clone().add(0, 2, 0).getBlock();

        Material type = current.getType();
        boolean isWater = type == Material.WATER || type == Material.BUBBLE_COLUMN
            || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS;
        boolean isLava = type == Material.LAVA;
        boolean isSolid = type.isSolid();

        if (allowSolid && isSolid) return true;
        if (!above1.getType().isAir() || !above2.getType().isAir()) return false;
        if (isWater && allowWater) return true;
        if (isLava && allowLava) return true;
        return isSolid;
    }
}
