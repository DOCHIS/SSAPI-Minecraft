package kr.ssapi.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class ItemSerializer {
    
    public static String serialize(ItemStack item) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }
    
    public static ItemStack deserialize(String data) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(data);
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ItemStack) dataInput.readObject();
        }
    }
} 