package kr.ssapi.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.json.JSONObject;

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