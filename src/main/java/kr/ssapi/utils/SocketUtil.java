package kr.ssapi.utils;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.thread.EventThread;
import kr.ssapi.config.MissionSettings;
import kr.ssapi.events.DonationEvent;
import kr.ssapi.events.MissionEvent;
import okhttp3.OkHttpClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;
import org.xerial.snappy.Snappy;

import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Socket.IO 연결 관리 유틸리티 — 연결, 로그인, 이벤트 수신, 재연결, 종료를 담당.
 *
 * <p>donation / mission 이벤트는 Snappy 압축 해제 후 Bukkit 메인 스레드로 이벤트 발행.
 * login 응답 미수신 시 타임아웃 후 재전송(sendLoginRequest 반복).
 */
public class SocketUtil {
    private static JavaPlugin plugin;
    private static Socket socket;
    private static OkHttpClient httpClient;
    private static volatile boolean loginResponseReceived = false;
    private static volatile boolean shuttingDown = false;
    private static ScheduledExecutorService executorService;

    // 플러그인 인스턴스와 단일 스레드 스케줄러를 초기화
    public static void init(JavaPlugin plugin) {
        SocketUtil.plugin = plugin;
        shuttingDown = false;
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ssapi-socket-scheduler");
                t.setDaemon(true);
                return t;
            });
        }
    }

    // 소켓 옵션 구성 후 연결 시작, 이벤트 핸들러(connect/disconnect/login/donation/mission) 등록
    public static Socket initializeSocket() {
        if (plugin == null) {
            init(JavaPlugin.getProvidingPlugin(SocketUtil.class));
        }
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        try {
            IO.Options opts = new IO.Options();
            opts.transports = new String[]{"websocket"};
            opts.forceNew = true;
            opts.timeout = cfg.getInt("api.socket.timeout_ms", 3000);
            opts.reconnection = cfg.getBoolean("api.socket.reconnect", true);
            opts.reconnectionAttempts = cfg.getInt("api.socket.reconnect_max_attempts", 10000);
            opts.reconnectionDelay = cfg.getInt("api.socket.reconnect_delay_ms", 1000);
            opts.reconnectionDelayMax = cfg.getInt("api.socket.reconnect_max_delay_ms", 30000);
            opts.callFactory = httpClient();
            opts.webSocketFactory = httpClient();

            socket = IO.socket(cfg.getString("api.servers.socket", "https://socket.ssapi.kr"), opts);

            socket.on(Socket.EVENT_CONNECT, args -> {
                if (shuttingDown) return;
                plugin.getLogger().info("Socket connected");
                loginResponseReceived = false;
                sendLoginRequest();
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                if (shuttingDown) return;
                plugin.getLogger().info("Socket disconnected");
                loginResponseReceived = false;
            });

            socket.on("login", args -> {
                if (shuttingDown) return;
                plugin.getLogger().info("Login response received");
                loginResponseReceived = true;
                handleRoomInfo(args);
            });

            socket.on("roomInfo", SocketUtil::handleRoomInfo);

            socket.on("donation", args -> handleCompressed(args, "donation",
                json -> Bukkit.getPluginManager().callEvent(new DonationEvent(json))));

            socket.on("mission", args -> handleCompressed(args, "mission", json -> {
                JSONObject settings = json.optJSONObject("mission_settings");
                if (settings != null) MissionSettings.update(settings);
                Bukkit.getPluginManager().callEvent(new MissionEvent(json));
            }));

            socket.connect();
            return socket;

        } catch (URISyntaxException e) {
            plugin.getLogger().log(Level.SEVERE, "Socket URI 오류", e);
            return null;
        }
    }

    private static OkHttpClient httpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.MINUTES)
                .build();
        }
        return httpClient;
    }

    // Snappy 압축 바이트 배열을 JSONObject 로 파싱 후 메인 스레드에서 handler 실행
    private static void handleCompressed(Object[] args, String eventType, java.util.function.Consumer<JSONObject> handler) {
        if (shuttingDown) return;
        try {
            byte[] compressed = (byte[]) args[0];
            JSONObject data = parseCompressedData(compressed);
            if (data != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (shuttingDown) return;
                    try { handler.accept(data); }
                    catch (Exception e) { plugin.getLogger().log(Level.WARNING, eventType + " 핸들러 실패", e); }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, eventType + " 데이터 처리 실패", e);
        }
    }

    private static void handleRoomInfo(Object[] args) {
        if (shuttingDown) return;
        if (args == null || args.length == 0) return;
        try {
            JSONObject roomInfo;
            if (args[0] instanceof byte[]) {
                roomInfo = parseCompressedData((byte[]) args[0]);
            } else if (args[0] instanceof JSONObject) {
                roomInfo = (JSONObject) args[0];
            } else {
                roomInfo = new JSONObject(String.valueOf(args[0]));
            }
            if (roomInfo != null) MissionSettings.update(roomInfo);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "roomInfo 파싱 실패", e);
        }
    }

    // API 키로 login 이벤트 전송 후 타임아웃 내 응답 없으면 재전송
    private static void sendLoginRequest() {
        if (shuttingDown || socket == null || plugin == null || !plugin.isEnabled()) return;
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        socket.emit("login", cfg.getString("api.key", ""));
        socket.emit("setReceiver", "login,donation,status,mission");

        executorService.schedule(() -> {
            if (!shuttingDown && !loginResponseReceived) {
                plugin.getLogger().info("Login response timeout, retrying...");
                sendLoginRequest();
            }
        }, cfg.getInt("api.socket.reconnect_delay_ms", 1000), TimeUnit.MILLISECONDS);
    }

    // logout 이벤트를 전송하고 소켓을 명시적으로 끊음
    public static void disconnect() {
        shuttingDown = true;
        if (socket != null) {
            try { socket.io().reconnection(false); } catch (Exception ignored) {}
            try {
                if (socket.connected()) socket.emit("logout");
            } catch (Exception ignored) {}
            try { socket.off(); } catch (Exception ignored) {}
            try { socket.offAnyIncoming(); } catch (Exception ignored) {}
            try { socket.offAnyOutgoing(); } catch (Exception ignored) {}
            try { socket.disconnect(); } catch (Exception ignored) {}
            socket = null;
        }
    }

    public static void shutdown() {
        try {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
                executorService.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executorService = null;

        OkHttpClient client = httpClient;
        // disconnect() 직후 cancelAll()을 호출하면 OkHttp가 WebSocket.onFailure를
        // 늦게 발행해 플러그인 classloader 종료 뒤 익명 콜백 클래스를 찾으려 한다.
        // 먼저 정상 close 콜백과 EventThread 큐를 bounded drain한다.
        awaitEventThreadDrain(2, TimeUnit.SECONDS);
        awaitTransportCallbacks(250, TimeUnit.MILLISECONDS);
        awaitEventThreadDrain(2, TimeUnit.SECONDS);

        if (client != null) {
            try {
                client.dispatcher().executorService().shutdown();
                if (!client.dispatcher().executorService().awaitTermination(2, TimeUnit.SECONDS)) {
                    client.dispatcher().cancelAll();
                    client.dispatcher().executorService().shutdownNow();
                    client.dispatcher().executorService().awaitTermination(1, TimeUnit.SECONDS);
                    awaitTransportCallbacks(100, TimeUnit.MILLISECONDS);
                    awaitEventThreadDrain(1, TimeUnit.SECONDS);
                }
                client.connectionPool().evictAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
            }
        }
        httpClient = null;
        loginResponseReceived = false;
        plugin = null;
    }

    private static void awaitTransportCallbacks(long timeout, TimeUnit unit) {
        try {
            unit.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean awaitEventThreadDrain(long timeout, TimeUnit unit) {
        CountDownLatch drained = new CountDownLatch(1);
        try {
            EventThread.nextTick(drained::countDown);
            boolean completed = drained.await(timeout, unit);
            if (!completed && plugin != null) {
                plugin.getLogger().warning("Socket.IO EventThread 종료 대기 시간이 초과됐습니다.");
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Throwable e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "Socket.IO EventThread 종료 대기 생략", e);
            }
            return false;
        }
    }

    // Snappy 바이트 배열을 압축 해제해 JSONObject 로 반환 (실패 시 null)
    public static JSONObject parseCompressedData(byte[] compressed) {
        try {
            String json = Snappy.uncompressString(compressed);
            return new JSONObject(json);
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "데이터 파싱 오류", e);
            }
            return null;
        }
    }

    public static Socket getSocket() { return socket; }

    public static boolean isConnected() { return socket != null && socket.connected(); }
}
