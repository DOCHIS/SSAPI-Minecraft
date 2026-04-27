package kr.ssapi.triggers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 트리거 scope 별 저장소.
 *
 * <ul>
 *   <li>{@link Scope#CONNECT} — /API 연동 직후 1회 (triggers.yml의 connect)
 *   <li>{@link Scope#DONATION} — 후원 들어올 때 (triggers.yml의 donation)
 *   <li>{@link Scope#MISSION_RECEIVE} — 미션 후원 들어올 때 (config.yml [3] mission.on_receive)
 *   <li>{@link Scope#MISSION_SETTLE} — 미션 정산 시 (config.yml [3] mission.on_settle)
 *   <li>{@link Scope#MISSION_RESULT} — 미션 결과 메타 (config.yml [3] mission.on_result)
 * </ul>
 */
public class TriggerRegistry {
    public enum Scope {
        CONNECT,
        DONATION,
        MISSION_RECEIVE,
        MISSION_SETTLE,
        MISSION_RESULT
    }

    private final Map<Scope, List<Trigger>> bucket = new EnumMap<>(Scope.class);

    // 특정 scope 의 트리거 목록을 교체 (TriggerLoader 가 리로드 시 호출)
    public synchronized void set(Scope scope, List<Trigger> triggers) {
        bucket.put(scope, triggers == null ? Collections.emptyList() : triggers);
    }

    public synchronized List<Trigger> get(Scope scope) {
        return bucket.getOrDefault(scope, Collections.emptyList());
    }

    public synchronized void clear() {
        bucket.clear();
    }
}
