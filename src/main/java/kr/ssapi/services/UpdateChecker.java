package kr.ssapi.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 신버전 알림 — onEnable 시 GitHub Releases API 1회 호출.
 *
 * <p>새 버전이 발견되면 OP 입장 시 클릭 가능한 채팅 알림.
 * 토글: config.yml {@code update_check.enabled}.
 */
public class UpdateChecker implements Listener {
    private static final String API_URL = "https://api.github.com/repos/dochis/SSAPI-Minecraft/releases/latest";

    private final JavaPlugin plugin;
    private final MessageService messages;
    private volatile String latestVersion;
    private volatile String releaseUrl;

    public UpdateChecker(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    // 비동기로 GitHub Releases API 를 호출해 최신 버전 확인
    public void check() {
        if (!plugin.getConfig().getBoolean("update_check.enabled", true)) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
                    con.setRequestMethod("GET");
                    con.setConnectTimeout(5000);
                    con.setReadTimeout(8000);
                    con.setRequestProperty("Accept", "application/vnd.github+json");
                    con.setRequestProperty("User-Agent", "SSAPI-Minecraft");
                    if (con.getResponseCode() != 200) return;
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        con.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    String tag = json.optString("tag_name", "").replace("v", "");
                    String url = json.optString("html_url", "");
                    if (!tag.isEmpty() && !tag.equals(plugin.getDescription().getVersion())) {
                        latestVersion = tag;
                        releaseUrl = url;
                        plugin.getLogger().info("새 버전 발견: " + tag + " — " + url);
                    }
                } catch (Exception e) {
                    plugin.getLogger().fine("업데이트 확인 실패: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // OP 플레이어 입장 시 새 버전 알림 메시지 전송
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (latestVersion == null || !p.isOp()) return;
        if (messages != null) {
            messages.send(p, "update_check.available", "version", latestVersion);
            if (releaseUrl != null) messages.send(p, "update_check.link", "url", releaseUrl);
        }
    }
}
