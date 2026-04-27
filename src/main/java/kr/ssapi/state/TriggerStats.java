package kr.ssapi.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 트리거 발화 통계 — 메모리 카운터.
 *
 * <p>StateManager 가 주기적으로 디스크에 flush. /api관리 상태 가 출력.
 */
public class TriggerStats {
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    // scope + triggerId 를 키로 발화 횟수를 1 증가
    public void record(String scope, String triggerId) {
        String key = scope + ":" + triggerId;
        counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    // 현재 카운터 전체를 변경 불가능한 스냅샷으로 반환 (StateManager 저장용)
    public Map<String, Long> snapshot() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> e : counters.entrySet()) {
            out.put(e.getKey(), e.getValue().get());
        }
        return out;
    }

    public void load(Map<String, Long> data) {
        counters.clear();
        if (data == null) return;
        for (Map.Entry<String, Long> e : data.entrySet()) {
            counters.put(e.getKey(), new AtomicLong(e.getValue()));
        }
    }
}
