package kr.ssapi.minecraft.SSAPI;

import io.socket.client.IO;
import io.socket.client.Socket;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.JSONException;
import org.json.JSONObject;
import org.xerial.snappy.Snappy;

import java.util.HashMap;

import static kr.ssapi.minecraft.DataBase.DonationData.DonationMap;
import static kr.ssapi.minecraft.DataBase.DonationData.DonationDataMap;
import static kr.ssapi.minecraft.Main.plugin;
import static kr.ssapi.minecraft.Util.OtherUtil.numberCom;

public class Soket {

    private String SOCKET_URL = "https://socket.ssapi.kr";
    private String SOCKET_API = "sack2022";

    public void SoketConnection() {
        try {
            IO.Options options = new IO.Options();
            options.transports = new String[]{"websocket"};
            options.timeout = 5000;
            options.reconnection = true;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 1000;
            options.reconnectionDelayMax = 5000;

            Socket socket = IO.socket(SOCKET_URL, options);

            socket.on(Socket.EVENT_CONNECT, args1 -> {
                Bukkit.getConsoleSender().sendMessage("§f[Donation] Soket Connection Successful");
                socket.emit("login", SOCKET_API);
            });

            socket.on(Socket.EVENT_DISCONNECT, args2 -> {
                String reason = args2[0].toString();
                System.err.println("§f[Donation] Socket connection disconnected: " + reason);
            });

            socket.on("donation", args3 -> {
                JSONObject json = StringJson((byte[]) args3[0]);

                try {
                    Player player = Bukkit.getPlayer(DonationMap.get(json.getString("streamer_id")));
                    String sponsorName = json.getString("nickname");
                    int count = (int) json.get("cnt");

                    if(player == null) return;

                    HashMap<String, Object> DataMap = DonationDataMap.getOrDefault(count, null);

                    if(DataMap == null) return;

                    String type = (String) DataMap.get("type");

                    if(type.equals("커맨드")) {
                        String command = (String) DataMap.get("command");

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String commandToExecute = command.replace("_", " ").replace("%player%", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                        });
                    }
                    if(type.equals("아이템")) player.getInventory().addItem((ItemStack) DataMap.get("item"));

                    player.sendTitle("§a§l" + numberCom(count) + "개 후원!!", "§f" + sponsorName + "님이 후원 하였습니다!", 20, 40, 20);
                    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 0.8F);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });

            socket.connect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JSONObject StringJson(byte[] compressed) {
        try {
            byte[] parseed = Snappy.uncompressString(compressed).getBytes("UTF-8");
            return new JSONObject(new String(parseed, "UTF-8"));
        } catch (Exception e) {
            System.out.println("[API] 파싱 오류 발생 / 원본 데이터: " + new String(compressed));
            e.printStackTrace();
            return null;
        }
    }
}
