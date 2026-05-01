package kr.ssapi;

import kr.ssapi.actions.ActionChain;
import kr.ssapi.actions.ActionFactory;
import kr.ssapi.commands.CommandRouter;
import kr.ssapi.commands.subcommands.*;
import kr.ssapi.gui.GuiManager;
import kr.ssapi.kits.KitManager;
import kr.ssapi.listeners.DonationListener;
import kr.ssapi.listeners.MissionListener;
import kr.ssapi.services.ApiClient;
import kr.ssapi.services.ApiErrorMapper;
import kr.ssapi.services.FileLogService;
import kr.ssapi.services.MessageService;
import kr.ssapi.services.Metrics;
import kr.ssapi.services.UpdateChecker;
import kr.ssapi.state.StateManager;
import kr.ssapi.state.TriggerStats;
import kr.ssapi.storage.StorageManager;
import kr.ssapi.triggers.TriggerLoader;
import kr.ssapi.triggers.TriggerRegistry;
import kr.ssapi.utils.SocketUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SSAPI 플러그인 메인 클래스.
 *
 * <p>플러그인 활성화(onEnable) 시 설정 검증, 스토리지, 소켓, 리스너, 커맨드를 순서대로 초기화.
 * 비활성화(onDisable) 시 상태 저장 → 소켓 해제 → 스토리지 종료 순으로 정리.
 */
public final class minecraft extends JavaPlugin {

    private MessageService messages;
    private KitManager kits;
    private TriggerRegistry triggers;
    private TriggerLoader triggerLoader;
    private ActionFactory actionFactory;
    private ActionChain actionChain;
    private GuiManager gui;
    private StateManager stateManager;
    private TriggerStats triggerStats;
    private Metrics metrics;
    private UpdateChecker updateChecker;
    private ApiClient apiClient;
    private ApiErrorMapper apiErrorMapper;
    private FileLogService fileLogs;

    @Override
    public void onEnable() {
        // 기본 리소스 추출 (config.yml / triggers.yml / kits.yml / messages.yml)
        saveDefaultConfig();
        for (String f : new String[]{"triggers.yml", "kits.yml", "messages.yml"}) {
            if (!new java.io.File(getDataFolder(), f).exists()) {
                try { saveResource(f, false); } catch (IllegalArgumentException ignored) {}
            }
        }
        // 시작 시 설정 검증 — 위반 시 경고 로그 (실행은 계속)
        kr.ssapi.config.ConfigValidator.Report startupValidation =
            kr.ssapi.config.ConfigValidator.validateConfig(getConfig());
        if (!startupValidation.ok()) {
            getLogger().warning("config.yml 검증 경고 - " + startupValidation.issues.size() + "건:");
            for (kr.ssapi.config.ConfigValidator.Issue i : startupValidation.issues) {
                getLogger().warning("  " + i);
            }
        }

        // 메시지 서비스
        messages = new MessageService(this);
        fileLogs = new FileLogService(this);

        // API 클라이언트
        apiClient = new ApiClient(
            getConfig().getString("api.servers.api", "https://api.ssapi.kr"),
            getConfig().getString("api.key", ""),
            fileLogs
        );
        apiErrorMapper = new ApiErrorMapper(messages);

        // 스토리지
        try {
            StorageManager.initialize(this);
            getLogger().info("스토리지 초기화 완료 (" + getConfig().getString("storage.type", "yml") + ")");
        } catch (Exception e) {
            getLogger().severe("스토리지 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // v2 코어 컴포넌트
        kits = new KitManager(this);
        triggers = new TriggerRegistry();
        triggerStats = new TriggerStats();
        triggerLoader = new TriggerLoader(this);
        triggerLoader.loadInto(triggers);

        actionFactory = new ActionFactory(this, kits, messages);
        actionChain = new ActionChain(actionFactory);

        gui = new GuiManager(this);
        stateManager = new StateManager(this, kits, triggerStats);
        stateManager.loadAll();

        // 소켓
        SocketUtil.init(this);
        if (SocketUtil.initializeSocket() != null) {
            getLogger().info("소켓 초기화 완료");
        } else {
            getLogger().severe("소켓 초기화 실패");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 리스너
        DonationListener donationListener = new DonationListener(this, triggers, actionChain, messages, fileLogs);
        getServer().getPluginManager().registerEvents(donationListener, this);
        getServer().getPluginManager().registerEvents(
            new MissionListener(this, triggers, actionChain, fileLogs), this);
        getServer().getPluginManager().registerEvents(gui, this);

        // 운영 기능
        metrics = new Metrics(this);
        metrics.enable();
        updateChecker = new UpdateChecker(this, messages);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.check();

        // 커맨드 — 3축 (api / api관리 / api테스트)
        registerUserCommand();
        registerAdminCommand();
        registerTestCommand();

        getLogger().info("SSAPI v" + getDescription().getVersion() + " 활성화 완료");
    }

    // /api 커맨드 — 일반 플레이어용 (연동/동시송출/시작/중지/상태)
    private void registerUserCommand() {
        CommandRouter router = new CommandRouter("api", messages);
        router
            .register(new ConnectSub(this, messages, apiClient, apiErrorMapper, false, triggers, actionChain))
            .register(new SimulcastConnectSub(this, messages, apiClient, apiErrorMapper, false))
            .register(new SimulcastDisconnectSub(this, messages, apiClient, apiErrorMapper, false))
            .register(new StartSub(messages, apiClient, apiErrorMapper, false))
            .register(new StopSub(messages, apiClient, apiErrorMapper, false))
            .register(new StatusSub(messages, false));
        getCommand("api").setExecutor(router);
        getCommand("api").setTabCompleter(router);
    }

    // /api관리 커맨드 — 관리자용 전체 기능 (리로드/저장/디버그/킷/트리거 등)
    private void registerAdminCommand() {
        CommandRouter router = new CommandRouter("api관리", messages);
        ReloadSub reload = new ReloadSub(this, messages, () -> {
            triggerLoader.loadInto(triggers);
            kits.reload();
            stateManager.saveAll();
        });
        router
            .register(new HelpSub(router))
            .register(new StatusSub(messages, true))
            .register(reload)
            .register(new SaveSub(this, messages, kits))
            .register(new DebugSub(this, messages))
            .register(new StartSub(messages, apiClient, apiErrorMapper, true))
            .register(new StopSub(messages, apiClient, apiErrorMapper, true))
            .register(new ConnectSub(this, messages, apiClient, apiErrorMapper, true, triggers, actionChain))
            .register(new SimulcastConnectSub(this, messages, apiClient, apiErrorMapper, true))
            .register(new SimulcastDisconnectSub(this, messages, apiClient, apiErrorMapper, true))
            .register(new ConnectionDeleteSub(this, messages, apiClient, apiErrorMapper))
            .register(new KitSub(gui, kits, messages))
            .register(new TriggerSub(gui, triggers, messages))
            .register(new MissionSub(messages))
            .register(new ConfigSub(this, messages));
        getCommand("api관리").setExecutor(router);
        getCommand("api관리").setTabCompleter(router);
    }

    // /api테스트 커맨드 — 개발/테스트용
    private void registerTestCommand() {
        CommandRouter router = new CommandRouter("api테스트", messages);
        router.register(new TestSub(this, messages));
        getCommand("api테스트").setExecutor(router);
        getCommand("api테스트").setTabCompleter(router);
    }

    @Override
    public void onDisable() {
        try {
            if (stateManager != null) stateManager.saveAll();
        } catch (Exception e) {
            getLogger().warning("state 저장 실패: " + e.getMessage());
        }

        SocketUtil.disconnect();
        SocketUtil.shutdown();
        getLogger().info("소켓 연결 해제");

        try {
            StorageManager.close();
            getLogger().info("스토리지 종료");
        } catch (Exception e) {
            getLogger().severe("스토리지 종료 실패: " + e.getMessage());
        }

        if (metrics != null) metrics.disable();

        getLogger().info("SSAPI 비활성화");
    }

    public MessageService getMessages() { return messages; }
    public KitManager getKitManager() { return kits; }
    public TriggerRegistry getTriggers() { return triggers; }
    public ActionChain getActionChain() { return actionChain; }
    public GuiManager getGuiManager() { return gui; }
    public StateManager getStateManager() { return stateManager; }
    public TriggerStats getTriggerStats() { return triggerStats; }
}
