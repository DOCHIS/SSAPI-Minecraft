package kr.ssapi.services;

import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * bStats 통합 — 익명 통계 (서버 수, MC 버전 분포 등).
 *
 * <p>bStats id 는 출간 시 발급. 토글: config.yml {@code metrics.enabled}.
 */
public class Metrics {
    // bStats 플러그인 ID — https://bstats.org/plugin/bukkit/SSApi/30958
    private static final int BSTATS_PLUGIN_ID = 30958;

    private final JavaPlugin plugin;
    private org.bstats.bukkit.Metrics metrics;

    public Metrics(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // bStats 활성화 — storage_type / settle_payout / mission_enabled 커스텀 차트 등록
    public void enable() {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) return;
        if (BSTATS_PLUGIN_ID <= 0) {
            plugin.getLogger().info("bStats id 미발급 — 통계 비활성화 (Metrics.java BSTATS_PLUGIN_ID 교체 필요)");
            return;
        }
        try {
            metrics = new org.bstats.bukkit.Metrics(plugin, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("storage_type",
                () -> plugin.getConfig().getString("storage.type", "yml")));
            metrics.addCustomChart(new SimplePie("mission_settle_payout",
                () -> plugin.getConfig().getString("mission.settle_payout", "combined")));
            metrics.addCustomChart(new SimplePie("mission_enabled",
                () -> String.valueOf(kr.ssapi.config.MissionSettings.get().enabled)));
        } catch (Throwable t) {
            plugin.getLogger().warning("bStats 초기화 실패 (무시): " + t.getMessage());
        }
    }

    public void disable() {
        // bStats 는 별도 shutdown API 없음
    }
}
