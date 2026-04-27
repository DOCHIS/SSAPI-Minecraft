package kr.ssapi.triggers;

import kr.ssapi.actions.ActionSpec;

import java.util.Collections;
import java.util.List;

/**
 * 트리거 정의 — donation / mission.on_receive / mission.on_settle / mission.on_result / connect 모두 같은 형태.
 *
 * <p>매칭 규칙 (TriggerMatcher 가 적용):
 * <ul>
 *   <li>enabled: false 면 매칭에서 제외
 *   <li>priority desc 정렬 (동률은 id 사전순)
 *   <li>stop_on_match: true 면 매칭된 첫 트리거에서 단락
 * </ul>
 */
public class Trigger {
    public final String id;
    public final boolean enabled;
    public final MatchSpec match;
    public final int priority;
    public final boolean stopOnMatch;
    public final List<ActionSpec> actions;

    public Trigger(String id, boolean enabled, MatchSpec match, int priority, boolean stopOnMatch, List<ActionSpec> actions) {
        this.id = id;
        this.enabled = enabled;
        this.match = match;
        this.priority = priority;
        this.stopOnMatch = stopOnMatch;
        this.actions = actions == null ? Collections.emptyList() : actions;
    }
}
