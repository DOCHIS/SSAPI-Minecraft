package kr.ssapi.minecraft.Commands;

import kr.ssapi.minecraft.DataBase.DonationData;
import kr.ssapi.minecraft.SSAPI.API;
import kr.ssapi.minecraft.Util.OtherUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static kr.ssapi.minecraft.DataBase.DonationData.DonationMap;
import static kr.ssapi.minecraft.DataBase.DonationData.DonationDataMap;
import static kr.ssapi.minecraft.Util.FileUtil.*;

public class DonationManagerCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)) {
            sender.sendMessage("해당 커맨드는 인게임 커맨드 입니다.");
            return false;
        }
        Player p = (Player) sender;
        if(!p.isOp()) return false;
        if(args.length == 0) {
            HelpUsage(p);
            return false;
        }
        if(args.length == 1) {
            if(Arrays.asList("추가", "삭제").contains(args[0])) {
                p.sendMessage("BJ ID를 입력하여 주시기 바랍니다.");
                return false;
            }
            if(Arrays.asList("타입", "커맨드", "아이템").contains(args[0])) {
                p.sendMessage("후원 갯수를 입력하여 주시기 바랍니다.");
                return false;
            }
        }
        if(args.length == 2) {
            if(args[0].equals("추가")) {
                p.sendMessage("유저 아이디를 입력하여 주시기 바랍니다.");
                return false;
            }
            if(args[0].equals("타입")) {
                p.sendMessage("타입을 입력하여 주시기 바랍니다.");
                return false;
            }
            if(args[0].equals("커맨드")) {
                p.sendMessage("커맨드를 입력하여 주시기 바랍니다.");
                return false;
            }
        }
        if(args.length == 3) {
            if(args[0].equals("타입")) {
                if(!Arrays.asList("커맨드", "아이템").contains(args[2])) {
                    p.sendMessage("타입은 커맨드 또는 아이템만 존재 합니다.");
                    return false;
                }
            }
        }

        if(Arrays.asList("타입", "커맨드", "아이템").contains(args[0])) {
            if(!OtherUtil.StringFormatInteger(args[1])) {
                p.sendMessage("갯수는 숫자만 입력이 가능합니다.");
                return false;
            }
        }
        if(args[0].equals("아이템")) {
            if(p.getInventory().getItemInMainHand().getType() == Material.AIR) {
                p.sendMessage("손에 아이템을 들고 커맨드를 입력하여 주시기 바랍니다.");
                return false;
            }
        }

        try {
            RunCommand(p, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private void HelpUsage(Player p) {
        p.sendMessage("/후원연동관리 추가 [BJID] [USERID] - 해당 BJ ID 및 USER ID값을 설정 합니다.");
        p.sendMessage("/후원연동관리 삭제 [BJID] - 해당 BJ ID 값을 삭제 합니다.");
        p.sendMessage("/후원연동관리 타입 [갯수] [커맨드/아이템] - 해당 갯수에 타입을 지정 합니다.");
        p.sendMessage("/후원연동관리 커맨드 [갯수] [커맨드] - 해당 갯수에 커맨드를 설정 합니다.");
        p.sendMessage("/후원연동관리 아이템 [갯수] - 해당 갯수에 아이템을 손에든 아이템으로 설정 합니다.");
        p.sendMessage("/후원연동관리 리로드 - 후원연동 시스템에 데이터를 리로드 합니다.");
    }

    private void RunCommand(Player p, String[] args) throws Exception {
        switch (args[0]) {
            case "추가": DonationUserSetting(p, args[1], args[2]); break;
            case "삭제": DonationUserDelete(p, args[1]); break;
            case "타입": DonationTypeSetting(p, Integer.parseInt(args[1]), args[2]); break;
            case "커맨드": DonationCommandSetting(p, Integer.parseInt(args[1]), args[2]); break;
            case "아이템": DonationItemSetting(p, Integer.parseInt(args[1])); break;
            case "리로드": DonationDataReload(p); break;
        }
    }

    private void DonationUserSetting(Player p, String ID, String User) throws Exception {
        DonationMap.put(ID, User);
        setdataFile("", "", "ID." + ID, User);

        boolean value = (boolean) new API().connectionAPI(ID);

        if(!value) {
            p.sendMessage("해당 후원 연동 API에 데이터가 연동되지 못하였습니다.");
            return;
        }

        p.sendMessage("해당 후원 연동 API에 데이터가 추가 되었습니다.");
    }

    private void DonationUserDelete(Player p, String ID) throws Exception {
        DonationMap.remove(ID);

        deleteFile("", "");
        createFile("", "");

        for (Map.Entry<String, String> entry: DonationMap.entrySet()) {
            String BJID = entry.getKey();
            String USERNAME = entry.getValue();

            setdataFile("", "", "ID." + BJID, USERNAME);
        }

        boolean value = (boolean) new API().disconnectAPI(ID);

        if(!value) {
            p.sendMessage("해당 후원 연동 API에 데이터가 연동되지 못하였습니다.");
            return;
        }

        p.sendMessage("해당 후원 연동 API에 데이터가 삭제 되었습니다.");
    }

    private void DonationTypeSetting(Player p, int amount, String type) {
        HashMap<String, Object> DataMap = DonationDataMap.getOrDefault(amount, new HashMap<>());

        DataMap.put("type", type);
        DonationDataMap.put(amount, DataMap);
        setdataFile("", "config", "Data." + amount + ".type", type);

        p.sendMessage("해당 갯수에 타입이 설정 되었습니다.");
    }

    private void DonationCommandSetting(Player p, int amount, String cmd) {
        HashMap<String, Object> DataMap = DonationDataMap.getOrDefault(amount, new HashMap<>());

        DataMap.put("command", cmd);
        DonationDataMap.put(amount, DataMap);
        setdataFile("", "config", "Data." + amount + ".command", cmd);

        p.sendMessage("해당 갯수에 커맨드가 설정 되었습니다.");
    }

    private void DonationItemSetting(Player p, int amount) {
        ItemStack item = p.getInventory().getItemInMainHand();
        HashMap<String, Object> DataMap = DonationDataMap.getOrDefault(amount, new HashMap<>());

        DataMap.put("item", item);
        DonationDataMap.put(amount, DataMap);
        setdataFile("", "config", "Data." + amount + ".item", item);

        p.sendMessage("해당 갯수에 아이템이 설정 되었습니다.");
    }

    private void DonationDataReload(Player p) {
        new DonationData().DataLoad();
        p.sendMessage("후원연동 데이터가 리로드 되었습니다.");
    }
}
