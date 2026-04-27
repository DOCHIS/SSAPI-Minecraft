package kr.ssapi.actions;

import java.util.List;

/**
 * 트리거의 actions: 배열을 순차 실행.
 *
 * <p>각 ActionSpec 을 ActionFactory 에 위임. 액션 자체의 비동기/sync 정책은
 * 각 Action 구현체가 책임 (예: CommandAction 은 메인 스레드에서 dispatch).
 */
public class ActionChain {
    private final ActionFactory factory;

    public ActionChain(ActionFactory factory) {
        this.factory = factory;
    }

    public void execute(List<ActionSpec> actions, ActionContext context) {
        if (actions == null) return;
        for (ActionSpec spec : actions) {
            try {
                factory.execute(spec, context);
            } catch (Exception e) {
                // 한 액션 실패가 다음 액션 실행을 막지 않도록
                java.util.logging.Logger.getLogger("SSApi")
                    .log(java.util.logging.Level.WARNING,
                        "ActionChain action 실패: " + (spec == null ? "null" : spec.type), e);
            }
        }
    }
}
