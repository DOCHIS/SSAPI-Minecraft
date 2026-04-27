package kr.ssapi.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * 사용자가 실수로 주석을 다 지웠을 때 — 현재 값은 유지 + 신규 템플릿의 주석을 다시 입혀줌.
 *
 * <p>동작:
 * <ol>
 *   <li>현재 파일을 .bak 으로 백업</li>
 *   <li>resources 의 v2 템플릿을 데이터폴더로 덮어쓰기 (주석 포함)</li>
 *   <li>백업 파일에서 키-값 추출 → 새 파일에 set/save</li>
 * </ol>
 *
 * <p>한계: Bukkit FileConfiguration save 는 set 한 키에 대해 인라인 주석을 보존하지 못함.
 * 그래도 헤더/섹션 주석은 신규 템플릿이 갖고 있으므로 대부분의 정보 회복 가능.
 */
public class ConfigRepair {

    private final JavaPlugin plugin;

    public ConfigRepair(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 현재 값을 유지한 채 리소스 템플릿을 덮어쓴 뒤 기존 키-값을 재적용
    /** config.yml 주석 복구. 결과 메시지 반환. */
    public String repair(String filename) {
        File dataFolder = plugin.getDataFolder();
        File target = new File(dataFolder, filename);
        if (!target.exists()) {
            return filename + " 가 존재하지 않습니다 — 복구 대상 없음";
        }

        InputStream resource = plugin.getResource(filename);
        if (resource == null) {
            return "리소스 템플릿 " + filename + " 을(를) 찾을 수 없습니다";
        }

        File backup = new File(dataFolder, filename + ".repair.bak");
        try {
            // 1) 백업
            Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 2) 기존 값 캐시
            FileConfiguration old = YamlConfiguration.loadConfiguration(target);

            // 3) 템플릿 덮어쓰기
            target.delete();
            plugin.saveResource(filename, true);

            // 4) 값 복원
            FileConfiguration fresh = YamlConfiguration.loadConfiguration(target);
            for (String key : old.getKeys(true)) {
                if (old.isConfigurationSection(key)) continue;
                fresh.set(key, old.get(key));
            }
            fresh.save(target);

            return filename + " 복구 완료. 백업: " + backup.getName();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ConfigRepair 실패: " + filename, e);
            return filename + " 복구 실패: " + e.getMessage();
        }
    }

    /** 단순히 리소스 템플릿을 추출해 콘솔에 보여주거나 별도 파일로 저장 */
    public String extractTemplate(String filename, File outFile) {
        InputStream in = plugin.getResource(filename);
        if (in == null) return "리소스 " + filename + " 을(를) 찾을 수 없습니다";
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            Files.write(outFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            return "템플릿을 " + outFile.getName() + " 으로 추출했습니다";
        } catch (IOException e) {
            return "추출 실패: " + e.getMessage();
        }
    }
}
