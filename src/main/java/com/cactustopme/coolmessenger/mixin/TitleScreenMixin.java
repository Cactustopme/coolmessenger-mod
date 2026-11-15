package com.cactustopme.coolmessenger.mixin;

import com.cactustopme.coolmessenger.client.gui.LoginScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void addCoolMessengerButton(CallbackInfo ci) {
        // Маленькая квадратная кнопка справа от Realms
        int btnSize = 20;
        int x = this.width / 2 + 105; // Правее центральной колонки
        int y = this.height / 4 + 48; // На уровне Realms

        this.addRenderableWidget(Button.builder(Component.literal("CM"), b -> {
            this.minecraft.setScreen(com.cactustopme.coolmessenger.CoolMessengerMod.CONFIG.isLoggedIn() ? 
                new com.cactustopme.coolmessenger.client.gui.ChatScreen() : new LoginScreen());
        }).bounds(x, y, btnSize, btnSize).build());
    }
}
