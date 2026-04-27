package kr.ssapi.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.json.JSONObject;

/**
 * 후원 수신 이벤트 — SocketUtil 이 "donation" 소켓 이벤트를 받으면 메인 스레드로 발행.
 *
 * <p>페이로드는 Snappy 압축 해제 후 JSON 으로 파싱된 후원 데이터.
 */
public class DonationEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final JSONObject donationData;

    public DonationEvent(JSONObject donationData) {
        this.donationData = donationData;
    }

    public JSONObject getDonationData() {
        return donationData;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
} 