package kr.ssapi.listeners;

import kr.ssapi.actions.*;
import kr.ssapi.config.Config;
import kr.ssapi.events.DonationEvent;
import kr.ssapi.model.ApiConnection;
import kr.ssapi.model.ApiLog;
import kr.ssapi.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import org.bukkit.Sound;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class DonationListener implements Listener {
    private final JavaPlugin plugin;
    private final Map<String, DonationAction> actions;
    private final Queue<RandomSelectionTask> selectionQueue = new LinkedList<>();
    private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);
    
    public DonationListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.actions = new HashMap<>();
        initializeActions();
    }
    
    private void initializeActions() {
        // 각 액션 초기화 및 플러그인 설정
        DonationAction giveItem = new GiveItemAction();
        DonationAction randomEffect = new RandomEffectAction();
        DonationAction spawnMob = new SpawnMobAction();
        DonationAction randomTeleport = new RandomTeleportAction();
        DonationAction instantDeath = new InstantDeathAction();
        
        // 플러그인 설정
        giveItem.setPlugin(plugin);
        randomEffect.setPlugin(plugin);
        spawnMob.setPlugin(plugin);
        randomTeleport.setPlugin(plugin);
        instantDeath.setPlugin(plugin);
        
        // 맵에 액션 등록
        actions.put("GIVE_ITEM", giveItem);
        actions.put("RANDOM_EFFECT", randomEffect);
        actions.put("SPAWN_MOB", spawnMob);
        actions.put("RANDOM_TELEPORT", randomTeleport);
        actions.put("INSTANT_DEATH", instantDeath);
    }

    private void executeDonationAction(Player player, String action, String username) {
        DonationAction donationAction = actions.get(action);
        if (donationAction != null) {
            if (donationAction instanceof InstantDeathAction) {
                ((InstantDeathAction) donationAction).setDonatorName(username);
            }
            donationAction.execute(player);
        }
    }

    @EventHandler
    public void onDonation(DonationEvent event) {
        JSONObject data = event.getDonationData();
        
        try {
            // JSON 데이터 파싱
            String streamerId = data.getString("streamer_id");
            String username = data.has("nickname") && !data.getString("nickname").isEmpty() 
                ? data.getString("nickname") 
                : "익명의 후원자";
            Integer cnt = data.getInt("cnt");
            Integer amount = data.getInt("amount");
            String platform = data.getString("platform");
            
            // 스트리머 연동 정보 조회
            ApiConnection.Platform platformEnum;
            if (platform.equalsIgnoreCase("soop")) {
                platformEnum = ApiConnection.Platform.숲;
            } else if (platform.equalsIgnoreCase("chzzk")) {
                platformEnum = ApiConnection.Platform.치지직;
            } else {
                return; // 알 수 없는 플랫폼
            }
            
            Optional<ApiConnection> connection = StorageManager.getDriver()
                .getConnectionByStreamerIdAndPlatform(streamerId, platformEnum);
            if (!connection.isPresent()) return;
            
            // 플레이어 접속 확인
            UUID playerUUID = UUID.fromString(connection.get().getUuid());
            Player player = Bukkit.getPlayer(playerUUID);
            
            // 서버 정보 가져오기
            String serverIp = "";
            String serverName = "";
            try {
                InetAddress inetAddress = InetAddress.getLocalHost();
                serverIp = inetAddress.getHostAddress();
                serverName = inetAddress.getHostName();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // ApiLog 객체 생성
            ApiLog log = new ApiLog(
                null,
                serverIp,
                serverName,
                streamerId,
                username,
                cnt,
                "donation",
                data.toString(),
                player != null ? ApiLog.IsRun.Y : ApiLog.IsRun.N,
                player != null ? player.getName() : connection.get().getName(),
                connection.get().getUuid(),
                player != null ? player.getWorld().getName() : null,
                LocalDateTime.now()
            );
            
            // 로그 저장
            StorageManager.getDriver().saveApiLog(log);
            
            // 플레이어가 접속 중인 경우 알림 메시지와 후원 동작 실행
            if (player != null) {
                String message = Config.getInstance().getMessage("messages.donation.format")
                    .replace("{username}", username)
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{cnt}", String.valueOf(cnt));
                
                if (!message.isEmpty()) {
                    player.sendMessage(message);
                    // 후원 메시지 알림음
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                
                // 후원 금액별 동작 실행
                Config config = Config.getInstance();
                Map<String, Object> amounts = plugin.getConfig().getConfigurationSection("donation-actions.amounts").getValues(false);
                
                for (Map.Entry<String, Object> entry : amounts.entrySet()) {
                    int requiredAmount = Integer.parseInt(entry.getKey());
                    if (amount == requiredAmount) { // 정확히 일치하는 금액만 처리
                        String action = entry.getValue().toString();
                        executeDonationAction(player, action, username);
                        break; // 일치하는 금액을 찾았으므로 반복 중단
                    }
                }
                
                // 도네이션 커맨드 실행
                if (config.isDonationCommandEnabled()) {
                    String command = config.getDonationCommandFormat()
                        .replace("{platform}", platform.toLowerCase())
                        .replace("{player}", player.getName())
                        .replace("{cnt}", String.valueOf(cnt))
                        .replace("{donator_name}", username)
                        .replace("{amount}", String.valueOf(amount))
                        .replace("{id}", data.optString("id", ""))
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{streamer_id}", streamerId)
                        .replace("{donator_id}", data.optString("donator_id", ""))
                        .replace("{message}", data.optString("message", "")
                    );
                    
                    // 서버의 메인 스레드에서 명령어 실행
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    });
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processNextInQueue() {
        if (isProcessingQueue.get() || selectionQueue.isEmpty()) {
            return;
        }
        
        isProcessingQueue.set(true);
        RandomSelectionTask task = selectionQueue.poll();
        if (task != null) {
            executeRandomSelection(task.player, task.options, task.onComplete);
        }
    }
    
    private void executeRandomSelection(Player player, List<?> options, Consumer<Object> onComplete) {
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger taskId = new AtomicInteger();
        Random random = new Random();
        
        // 선택 시작 소리
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        
        taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (count.get() >= 20) {
                Bukkit.getScheduler().cancelTask(taskId.get());
                Object selected = options.get(random.nextInt(options.size()));
                player.sendTitle("§e§l당첨!", "§f" + selected.toString(), 10, 40, 10);
                // 선택 완료 소리
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                onComplete.accept(selected);
                
                // 현재 작업 완료 후 다음 작업 처리
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    isProcessingQueue.set(false);
                    processNextInQueue();
                }, 60L); // 3초 후에 다음 작업 처리
                return;
            }
            
            // 랜덤 선택 효과
            Object randomOption = options.get(random.nextInt(options.size()));
            player.sendTitle("§7§l선택 중...", "§f" + randomOption.toString(), 0, 5, 0);
            // 선택 중 소리
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            count.incrementAndGet();
        }, 0L, 2L));
    }

    private void showRandomSelectionTitle(Player player, List<?> options, Consumer<Object> onComplete) {
        // 큐에 새로운 작업 추가
        selectionQueue.offer(new RandomSelectionTask(player, options, onComplete));
        // 큐 처리 시작
        processNextInQueue();
    }

    private static class RandomSelectionTask {
        final Player player;
        final List<?> options;
        final Consumer<Object> onComplete;
        
        RandomSelectionTask(Player player, List<?> options, Consumer<Object> onComplete) {
            this.player = player;
            this.options = options;
            this.onComplete = onComplete;
        }
    }

    // 테스트용 메소드 추가
    public void testDonationAction(Player sender, String input, String targetPlayerName) {
        // 대상 플레이어 결정
        Player targetPlayer = targetPlayerName != null ? 
            Bukkit.getPlayer(targetPlayerName) : sender;
            
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("§c대상 플레이어를 찾을 수 없습니다.");
            return;
        }

        // 입력값이 액션명인지 금액인지 확인
        if (actions.containsKey(input.toUpperCase())) {
            // 액션명으로 직접 실행
            executeDonationAction(targetPlayer, input.toUpperCase(), null);
            sender.sendMessage("§a" + targetPlayer.getName() + "님에게 " + input + " 액션을 실행했습니다.");
        } else {
            try {
                // 금액으로 실행
                int amount = Integer.parseInt(input);
                boolean found = false;
                
                Map<String, Object> amounts = plugin.getConfig()
                    .getConfigurationSection("donation-actions.amounts")
                    .getValues(false);
                
                for (Map.Entry<String, Object> entry : amounts.entrySet()) {
                    int requiredAmount = Integer.parseInt(entry.getKey());
                    if (amount == requiredAmount) {
                        String action = entry.getValue().toString();
                        executeDonationAction(targetPlayer, action, null);
                        sender.sendMessage("§a" + targetPlayer.getName() + 
                            "님에게 " + amount + "원 후원에 해당하는 " + 
                            action + " 액션을 실행했습니다.");
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    sender.sendMessage("§c해당 금액에 설정된 액션이 없습니다.");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c올바른 액션명 또는 금액을 입력해주세요.");
                sender.sendMessage("§7사용 가능한 액션: GIVE_ITEM, RANDOM_EFFECT, " + 
                    "SPAWN_MOB, RANDOM_TELEPORT, INSTANT_DEATH");
            }
        }
    }
} 