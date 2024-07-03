package kr.ssapi.minecraft;

import kr.ssapi.minecraft.Commands.DonationManagerCommand;
import kr.ssapi.minecraft.DataBase.DonationData;
import kr.ssapi.minecraft.SSAPI.Soket;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    public static Main plugin;
    @Override
    public void onEnable(){
        plugin = this;
        this.saveDefaultConfig();

        new Soket().SoketConnection();
        new DonationData().DataLoad();

        Bukkit.getServer().getPluginCommand("후원연동관리").setExecutor(new DonationManagerCommand());

        Bukkit.getConsoleSender().sendMessage("§a[§6SSAPI§a]§f " + getDescription().getVersion() + " §a활성화");
    }
    @Override
    public void onDisable(){
        Bukkit.getConsoleSender().sendMessage("§a[§6SSAPI§a]§f " + getDescription().getVersion() + " §c비활성화");
    }
}
