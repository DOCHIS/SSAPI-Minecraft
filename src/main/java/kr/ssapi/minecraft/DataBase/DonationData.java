package kr.ssapi.minecraft.DataBase;

import kr.ssapi.minecraft.Main;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Set;

import static kr.ssapi.minecraft.Util.FileUtil.*;

public class DonationData {

    public static HashMap<String, String> DonationMap = new HashMap<>();
    public static HashMap<Integer, HashMap<String, Object>> DonationDataMap = new HashMap<>();

    public void DataLoad() {
        if(!existsFile("", "")) Main.plugin.saveResource(".yml", false);

        DonationMapLoad();
        DonationDataMapLoad();
    }

    private void DonationMapLoad() {
        if(!getKeyExists("", "", "ID")) return;

        Set<String> players = getListKeys("", "", "ID");

        for (String player: players) {
            String playerName = (String) getdataFile("", "", "ID." + player);
            DonationMap.put(player, playerName);

            Bukkit.getConsoleSender().sendMessage("§a[§6SSAPI§a]§f " + playerName + "님 후원 API 연동 §a성공");
        }
    }

    private void DonationDataMapLoad() {
        if(!getKeyExists("", "config", "Data")) return;

        Set<String> amounts = getListKeys("", "config", "Data");

        for (String amount: amounts) {

            Set<String> keys = getListKeys("", "config", "Data." + amount);
            HashMap<String, Object> DataMap = new HashMap<>();

            for (String key: keys) {
                Object value = getdataFile("", "config", "Data." + amount + "." + key);
                DataMap.put(key, value);
            }

            DonationDataMap.put(Integer.parseInt(amount), DataMap);
        }
    }
}
