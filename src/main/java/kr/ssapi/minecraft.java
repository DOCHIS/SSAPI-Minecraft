package kr.ssapi;

import kr.ssapi.commands.OpCommand;
import kr.ssapi.commands.ConnectCommand;
import kr.ssapi.commands.TestCommand;
import kr.ssapi.config.Config;
import kr.ssapi.listeners.DonationListener;
import kr.ssapi.storage.StorageManager;
import kr.ssapi.utils.SocketUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class minecraft extends JavaPlugin {

    @Override
    public void onEnable() {
        // 설정 파일 초기화
        saveDefaultConfig();
        Config.init(this);

        // 스토리지 매니저 초기화
        try {
            StorageManager.initialize(this);
            getLogger().info("스토리지 드라이버가 성공적으로 초기화되었습니다. (" + Config.getInstance().getStorageType() + ")");
        } catch (Exception e) {
            getLogger().severe("스토리지 드라이버 초기화 중 오류가 발생했습니다: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 소켓 초기화
        if (SocketUtil.initializeSocket() != null) {
            getLogger().info("소켓이 성공적으로 초기화되었습니다.");
        } else {
            getLogger().severe("소켓 초기화 중 오류가 발생했습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 이벤트 리스너 등록
        DonationListener donationListener = new DonationListener(this);
        getServer().getPluginManager().registerEvents(donationListener, this);

        // 명령어 등록
        ConnectCommand connectCommand = new ConnectCommand();
        getCommand("api").setExecutor(connectCommand);
        getCommand("api").setTabCompleter(connectCommand);

        OpCommand adminCommand = new OpCommand(this);
        getCommand("api관리").setExecutor(adminCommand);
        getCommand("api관리").setTabCompleter(adminCommand);

        // API 테스트 명령어 등록
        TestCommand testCommand = new TestCommand(donationListener, this);
        getCommand("api테스트").setExecutor(testCommand);
        getCommand("api테스트").setTabCompleter(testCommand);

        getLogger().info("플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        // 소켓 연결 해제
        SocketUtil.disconnect();
        getLogger().info("소켓 연결이 해제되었습니다.");

        try {
            StorageManager.close();
            getLogger().info("스토리지 드라이버가 성공적으로 종료되었습니다.");
        } catch (Exception e) {
            getLogger().severe("스토리지 드라이버 종료 중 오류가 발생했습니다: " + e.getMessage());
        }

        getLogger().info("플러그인이 비활성화되었습니다.");
    }
}
