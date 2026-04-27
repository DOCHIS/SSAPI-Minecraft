package kr.ssapi.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.json.JSONObject;

/**
 * 미션 이벤트 — SocketUtil 이 socket.on("mission") 시 메인 스레드로 발행.
 *
 * <p>페이로드는 SOOP / Chzzk 통합 스키마. mission_phase 로 시점 구분 (receive / settle / result).
 * settle 시점은 data.settle.donors[] 후원자 명단 포함.
 */
public class MissionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final JSONObject data;

    public MissionEvent(JSONObject data) {
        this.data = data;
    }

    public JSONObject getMissionData() { return data; }

    public String getPhase() {
        return data == null ? null : data.optString("mission_phase", null);
    }

    public String getMissionType() {
        return data == null ? null : data.optString("mission_type", null);
    }

    public String getStreamerId() {
        return data == null ? null : data.optString("streamer_id", null);
    }

    public String getPlatform() {
        return data == null ? null : data.optString("platform", null);
    }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
