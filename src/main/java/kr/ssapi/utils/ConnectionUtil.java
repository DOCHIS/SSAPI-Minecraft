package kr.ssapi.utils;

import kr.ssapi.config.Config;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.storage.StorageDriver;
import kr.ssapi.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.time.LocalDateTime;
import java.util.Optional;

public class ConnectionUtil {
    private static final StorageDriver storage = StorageManager.getDriver();
    private static final Config config = Config.getInstance();

    public static boolean connectApi(Player player, CommandSender sender) {
        String message = config.getMessage("messages.connect.connecting");
        if (!message.isEmpty()) sender.sendMessage(message);

        try {
            Optional<ApiConnection> connection = storage.getConnectionByUuid(player.getUniqueId().toString());
            if (!connection.isPresent()) {
                message = config.getMessage("messages.connect.not-connected");
                if (!message.isEmpty()) sender.sendMessage(message);
                return false;
            }

            ApiConnection apiConnection = connection.get();
            String apiPlatform = apiConnection.getPlatform() == ApiConnection.Platform.숲 ? "soop" : "chzzk";

            Object result = Api.connectApi(apiPlatform, apiConnection.getStreamerId());
            if (result instanceof String) {
                sender.sendMessage(String.valueOf(result));
                return false;
            }

            sender.sendMessage(config.getMessage("messages.connect.connect-start"));
            sender.sendMessage(config.getMessage("messages.connect.connect-test"));
            return true;

        } catch (Exception e) {
            message = config.getMessage("messages.connect.connect-error");
            if (!message.isEmpty()) sender.sendMessage(message);
            e.printStackTrace();
            return false;
        }
    }

    public static boolean disconnectApi(Player player, CommandSender sender) {
        String message = config.getMessage("messages.connect.disconnecting");
        if (!message.isEmpty()) sender.sendMessage(message);

        try {
            Optional<ApiConnection> connection = storage.getConnectionByUuid(player.getUniqueId().toString());
            if (!connection.isPresent()) {
                message = config.getMessage("messages.connect.not-connected");
                if (!message.isEmpty()) sender.sendMessage(message);
                return false;
            }

            ApiConnection apiConnection = connection.get();
            String apiPlatform = apiConnection.getPlatform() == ApiConnection.Platform.숲 ? "soop" : "chzzk";

            Object result = Api.disconnectApi(apiPlatform, apiConnection.getStreamerId());
            if (result instanceof String) {
                sender.sendMessage(String.valueOf(result));
                return false;
            }

            sender.sendMessage(config.getMessage("messages.connect.disconnect-success"));
            return true;

        } catch (Exception e) {
            message = config.getMessage("messages.connect.disconnect-error");
            if (!message.isEmpty()) sender.sendMessage(message);
            e.printStackTrace();
            return false;
        }
    }

    public static boolean connect(Player player, String platformStr, String streamerId, CommandSender sender) {
        ApiConnection.Platform platform;
        try {
            platform = ApiConnection.Platform.valueOf(platformStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(config.getMessage("messages.connect.platform-invalid"));
            return false;
        }

        if (platform == ApiConnection.Platform.숲) {
            if (!streamerId.matches("^[a-z0-9_-]*$")) {
                sender.sendMessage(config.getMessage("messages.connect.soop-id-invalid"));
                return false;
            }
        } else {
            if (!streamerId.matches("^[a-zA-Z0-9]{12,32}$")) {
                sender.sendMessage(config.getMessage("messages.connect.chzzk-id-invalid"));
                return false;
            }
        }

        String streamerName;
        try {
            streamerName = platform == ApiConnection.Platform.숲 ? 
                Api.getSoopUserNick(streamerId) : 
                Api.getChzzkChannelInfo(streamerId);
            
            if (streamerName == null) {
                sender.sendMessage(config.getMessage("messages.connect.channel-info-fail")
                    .replace("{platform}", platform.name()));
                return false;
            }

            if (config.isStreamerNickCommandEnabled()) {
                String cleanNickname = streamerName.replaceAll("[^a-zA-Z0-9가-힣]", "");
                String command = config.getStreamerNickCommandFormat()
                    .replace("{player}", player.getName())
                    .replace("{nickname}", cleanNickname);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } catch (Exception e) {
            sender.sendMessage(config.getMessage("messages.connect.channel-info-fail")
                .replace("{platform}", platform.name()));
            e.printStackTrace();
            return false;
        }

        Optional<ApiConnection> existingConnection = storage.getConnectionByUuid(player.getUniqueId().toString());
        if (existingConnection.isPresent()) {
            try {
                storage.deleteConnection(player.getUniqueId().toString());
            } catch (Exception e) {
                sender.sendMessage(config.getMessage("messages.connect.delete-data-fail"));
                return false;
            }
        }

        ApiConnection newConnection = new ApiConnection(
            player.getUniqueId().toString(),
            platform,
            streamerId,
            streamerName,
            player.getName(),
            LocalDateTime.now()
        );

        try {
            storage.saveConnection(newConnection);
        } catch (Exception e) {
            sender.sendMessage(config.getMessage("messages.connect.save-data-fail"));
            return false;
        }

        sender.sendMessage(config.getMessage("messages.connect.connect-success"));
        return true;
    }
} 