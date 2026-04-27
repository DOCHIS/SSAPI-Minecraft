package kr.ssapi.config;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 서버가 push 한 미션 훅 설정 (대시보드 socket-rooms.mission 동봉).
 *
 * <p>SocketUtil 이 login/roomInfo 응답에서 mission 필드를 파싱해 update().
 * volatile snapshot 으로 atomic 갱신 — 어떤 스레드에서 읽어도 일관 view.
 */
public class MissionSettings {
    public static class Snapshot {
        public final boolean enabled;
        public final List<String> hookTimings;

        public Snapshot(boolean enabled, List<String> hookTimings) {
            this.enabled = enabled;
            this.hookTimings = hookTimings == null
                ? Collections.singletonList("settle")
                : Collections.unmodifiableList(new ArrayList<>(hookTimings));
        }

        public boolean isPhaseAllowed(String phase) {
            if (!enabled) return false;
            if (phase == null) return false;
            return hookTimings.contains(phase);
        }
    }

    private static volatile Snapshot snapshot = new Snapshot(true, Collections.singletonList("settle"));

    public static Snapshot get() { return snapshot; }

    // roomInfo JSON 에서 mission 설정을 파싱해 snapshot 을 원자적으로 교체
    public static void update(JSONObject roomInfo) {
        if (roomInfo == null) return;
        JSONObject mission = roomInfo.optJSONObject("mission");
        if (mission == null) return;

        boolean enabled = mission.optBoolean("enabled", true);
        List<String> timings = new ArrayList<>();
        JSONArray arr = mission.optJSONArray("hook_timings");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, null);
                if (s != null && !s.isEmpty()) timings.add(s);
            }
        }
        if (timings.isEmpty()) timings.add("settle");

        snapshot = new Snapshot(enabled, timings);
    }
}
