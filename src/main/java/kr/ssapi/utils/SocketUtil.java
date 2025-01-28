package kr.ssapi.utils;

import io.socket.client.IO;
import io.socket.client.Socket;
import kr.ssapi.config.Config;
import kr.ssapi.events.DonationEvent;
import org.bukkit.Bukkit;
import org.json.JSONObject;
import org.xerial.snappy.Snappy;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SocketUtil {
    private static Socket socket;
    private static boolean loginResponseReceived = false;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static Socket initializeSocket() {
        Config config = Config.getInstance();
        try {
            IO.Options opts = new IO.Options();
            opts.transports = new String[]{"websocket"};
            opts.timeout = config.getSocketTimeout();
            opts.reconnection = config.isSocketReconnectionEnabled();
            opts.reconnectionAttempts = config.getSocketReconnectionAttempts();
            opts.reconnectionDelay = config.getSocketReconnectionDelay();
            opts.reconnectionDelayMax = config.getSocketReconnectionMaxDelay();

            socket = IO.socket(config.getSocketServer(), opts);

            socket.on(Socket.EVENT_CONNECT, args -> {
                System.out.println("[API] Connected to socket server");
                loginResponseReceived = false;
                sendLoginRequest();
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                System.out.println("[API] Disconnected from socket server");
                loginResponseReceived = false;
            });

            socket.on("login", args -> {
                System.out.println("[API] Login response received");
                loginResponseReceived = true;
            });

            socket.on("donation", args -> {
                try {
                    byte[] compressed = (byte[]) args[0];
                    JSONObject donationData = parseCompressedData(compressed);
                    if (donationData != null) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SSApi"), () -> {
                            DonationEvent event = new DonationEvent(donationData);
                            Bukkit.getPluginManager().callEvent(event);
                        });
                    }
                } catch (Exception e) {
                    System.out.println("[API] 후원 데이터 처리 중 오류 발생");
                    e.printStackTrace();
                }
            });

            socket.connect();
            return socket;

        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void sendLoginRequest() {
        Config config = Config.getInstance();
        socket.emit("login", config.getApiKey());
        socket.emit("setReceiver", "login,donation,status");

        executorService.schedule(() -> {
            if (!loginResponseReceived) {
                System.out.println("[API] Login response timeout, retrying...");
                sendLoginRequest();
            }
        }, config.getSocketLoginRetryDelay(), TimeUnit.MILLISECONDS);
    }

    public static void disconnect() {
        if (socket != null && socket.connected()) {
            socket.emit("logout");
            socket.disconnect();
        }
    }

    public static JSONObject parseCompressedData(byte[] compressed) {
        try {
            byte[] decompressed = Snappy.uncompressString(compressed).getBytes("UTF-8");
            return new JSONObject(new String(decompressed, "UTF-8"));
        } catch (Exception e) {
            System.out.println("[API] 데이터 파싱 오류 발생 / 원본 데이터: " + new String(compressed));
            e.printStackTrace();
            return null;
        }
    }

    public static Socket getSocket() {
        return socket;
    }

    public static boolean isConnected() {
        return socket != null && socket.connected();
    }
} 