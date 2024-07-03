package kr.ssapi.minecraft.Util;

import kr.ssapi.minecraft.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileUtil {
    private static File file;
    private static FileConfiguration config;

    public static boolean existeFolder(String dir) {
        file = new File(Main.plugin.getDataFolder(), "/" + dir);
        if(file.exists()) {
            return true;
        }
        return false;
    }
    public static boolean existsFile(String dir, String path) {
        File folder = new File(Main.plugin.getDataFolder(), "/" + dir);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        if (file.exists()) {
            return true;
        }
        return false;
    }

    public static boolean createFolder(String dir) {
        file = new File(Main.plugin.getDataFolder(), "/" + dir);
        file.mkdirs();
        return false;
    }

    public static void createFile(String dir, String path){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        if(!existsFile(dir, path)){
            try {
                file.createNewFile();
                config.load(file);
            } catch (Exception e) { return; }
        }
    }

    public static void deleteFile(String dir, String path){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        if(existsFile(dir, path)) {
            file.delete();
        }
    }
    public static void savedataFile(String dir, String path){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        if(existsFile(dir, path)){
            try {
                config.save(file);
            }catch (Exception e){
                return;
            }
        }
    }
    public static void setdataFile(String dir, String path, String key, Object value){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        if(existsFile(dir, path)){
            config = YamlConfiguration.loadConfiguration(file);
            config.set(key, value);
            savedataFile(dir, path);
        }
    }
    public static Object getdataFile(String dir, String path, String key){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        if(existsFile(dir, path)){
            config = YamlConfiguration.loadConfiguration(file);
            return config.get(key);
        }
        return null;
    }
    public static List<String> getfileList(String dir){
        List<String> tempList = new ArrayList<>();
        File folder = new File(Main.plugin.getDataFolder(), "/" + dir);
        File files[] = folder.listFiles();
        assert files != null;
        for (File path: files) {
            file = path;
            tempList.add(file.getName().replace(".yml", ""));
        }
        return tempList;
    }
    public static boolean getKeyExists(String dir, String path, String key){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        config = YamlConfiguration.loadConfiguration(file);
        return config.contains(key);
    }
    public static Set<String> getListKeys(String dir, String path, String key){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection keys = config.getConfigurationSection(key);
        Set<String> texts = keys.getKeys(false);
        return texts;
    }

    public static List<String> getKeyStrings(String dir, String path, String key){
        file = new File(Main.plugin.getDataFolder(), "/" + dir + "/" + path + ".yml");
        config = YamlConfiguration.loadConfiguration(file);
        List<String> texts = config.getStringList(key);
        return texts;
    }
}
