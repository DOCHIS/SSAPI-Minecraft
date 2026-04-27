package kr.ssapi.actions;

/**
 * 액션 인터페이스 — 트리거가 발화(fire)될 때 실행되는 단위 작업.
 *
 * <p>ActionFactory 에 등록된 구현체(CommandAction, GiveKitAction 등)가 이 인터페이스를 구현.
 */
public interface Action {
    void execute(ActionSpec spec, ActionContext context);
}
