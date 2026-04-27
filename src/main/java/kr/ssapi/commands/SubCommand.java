package kr.ssapi.commands;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * 서브커맨드 인터페이스 — CommandRouter 가 dispatch.
 *
 * <p>실행 결과는 ExecutionResult enum 으로 반환. SUCCESS 면 usage 출력 안 함.
 * USAGE_ERROR 면 router 가 자동으로 도움말 표시. DENIED 는 권한 거부 메시지.
 */
public interface SubCommand {
    enum ExecutionResult { SUCCESS, USAGE_ERROR, DENIED }

    /** 명령어 이름 (예: "연동") */
    String name();

    /** 권한 노드 (예: "ssapi.command.connect"). null 이면 기본 사용 가능. */
    default String permission() { return null; }

    /** 별칭 */
    default List<String> aliases() { return Collections.emptyList(); }

    /** 도움말 한 줄 */
    String shortDescription();

    /** 사용법 */
    default String usage() { return "/" + name(); }

    ExecutionResult execute(CommandSender sender, String[] args);

    /** 탭완성 */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
