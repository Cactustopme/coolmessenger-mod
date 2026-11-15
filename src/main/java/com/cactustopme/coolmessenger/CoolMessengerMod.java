package com.cactustopme.coolmessenger;

import com.cactustopme.coolmessenger.client.MessengerApi;
import com.cactustopme.coolmessenger.client.MessengerWs;
import com.cactustopme.coolmessenger.client.gui.ChatScreen;
import com.cactustopme.coolmessenger.client.gui.LoginScreen;
import com.cactustopme.coolmessenger.config.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoolMessengerMod implements ClientModInitializer {
    public static final String MOD_ID = "coolmessenger";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static final MessengerApi API = new MessengerApi();
    public static final MessengerWs WS = new MessengerWs();
    public static ModConfig CONFIG;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        CONFIG = ModConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.coolmessenger.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(CONFIG.isLoggedIn() ? new ChatScreen() : new LoginScreen());
                }
            }
            WS.tick();
        });

        LOG.info("[CoolMessenger] Инициализирован");
    }
}
