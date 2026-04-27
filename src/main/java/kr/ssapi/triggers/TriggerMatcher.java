package kr.ssapi.triggers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 트리거 매칭 엔진.
 *
 * <p>amount 와 트리거 목록을 받아 매칭되는 트리거들을 priority desc 순으로 반환.
 * stop_on_match: true 인 트리거가 매칭되면 그 시점에 단락.
 */
public class TriggerMatcher {

    /**
     * @param amount   매칭 amount (donation 금액, mission combined/individual 금액 등)
     * @param triggers 후보 트리거 목록 (해당 scope)
     * @return 매칭된 트리거들 (priority desc, stop_on_match 적용)
     */
    public static List<Trigger> match(long amount, List<Trigger> triggers) {
        if (triggers == null || triggers.isEmpty()) return new ArrayList<>();

        // priority desc, 동률은 id 알파벳순 (안정성)
        List<Trigger> sorted = new ArrayList<>(triggers);
        sorted.sort(
            Comparator.comparingInt((Trigger t) -> -t.priority)
                .thenComparing((Trigger t) -> t.id == null ? "" : t.id)
        );

        List<Trigger> result = new ArrayList<>();
        for (Trigger t : sorted) {
            if (!t.enabled) continue;
            if (t.match == null || !t.match.matches(amount)) continue;
            result.add(t);
            if (t.stopOnMatch) break;
        }
        return result;
    }
}
