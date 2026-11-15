package com.cactustopme.coolmessenger.config;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("coolmessenger.json");

    public String uuid;
    public String sessionID;
    public String refreshToken;
    public String deviceID;
    public String username;
    public String token;

    public static ModConfig load() {
        try {
            if (Files.exists(PATH)) {
                ModConfig cfg = GSON.fromJson(Files.readString(PATH), ModConfig.class);
                if (cfg != null) return cfg;
            }
        } catch (Exception e) {
            CoolMessengerMod.LOG.warn("[Config] Не удалось загрузить", e);
        }
        return new ModConfig();
    }

    public void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            CoolMessengerMod.LOG.warn("[Config] Не удалось сохранить", e);
        }
    }

    public boolean isLoggedIn() {
        return sessionID != null && !sessionID.isEmpty();
    }

    public void clear() {
        uuid = sessionID = refreshToken = deviceID = username = token = null;
        save();
    }
}
