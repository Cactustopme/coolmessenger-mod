package com.cactustopme.coolmessenger.client;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import com.cactustopme.coolmessenger.config.ModConfig;

public class MessengerApiRefresher {
    public static void refresh(ModConfig cfg, Runnable onSuccess) {
        CoolMessengerMod.API.refreshSession(cfg.deviceID, cfg.refreshToken)
            .thenAccept(resp -> {
                String result = resp.has("result") ? resp.get("result").getAsString() : "";
                if (!"SUCCESS".equals(result)) {
                    cfg.clear();
                    return;
                }
                cfg.sessionID = resp.get("sessionID").getAsString();
                cfg.refreshToken = resp.get("refreshToken").getAsString();
                cfg.save();
                onSuccess.run();
            })
            .exceptionally(e -> {
                CoolMessengerMod.LOG.error("[API] refresh failed", e);
                cfg.clear();
                return null;
            });
    }
}
