package kr.ssapi.actions;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 도네이션 액션을 정의하는 인터페이스
 * 모든 도네이션 관련 액션은 이 인터페이스를 구현해야 합니다.
 */
public interface DonationAction {
    /**
     * 플러그인 인스턴스를 설정합니다.
     * 
     * @param plugin JavaPlugin 인스턴스
     */
    void setPlugin(JavaPlugin plugin);
    
    /**
     * 도네이션 액션을 실행합니다.
     * 
     * @param player 액션을 실행할 플레이어
     */
    void execute(Player player);
} 