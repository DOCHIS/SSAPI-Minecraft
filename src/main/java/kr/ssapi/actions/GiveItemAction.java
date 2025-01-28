package kr.ssapi.actions;

import kr.ssapi.config.Config;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.ByteArrayInputStream;
import java.util.Base64;

public class GiveItemAction implements DonationAction {
    private JavaPlugin plugin;

    @Override
    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player) {
        Config config = Config.getInstance();
        String itemData = config.getGiveItemData();
        if (!itemData.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(itemData);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedBytes);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                
                ItemStack item = (ItemStack) dataInput.readObject();
                dataInput.close();
                
                player.getInventory().addItem(item);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                
                String itemMessage = config.getMessage("messages.actions.give-item.success");
                if (!itemMessage.isEmpty()) {
                    player.sendMessage(itemMessage);
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = config.getMessage("messages.actions.give-item.error");
                if (!errorMessage.isEmpty()) {
                    player.sendMessage(errorMessage);
                }
            }
        }
    }
}