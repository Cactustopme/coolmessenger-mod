package com.cactustopme.coolmessenger.client.gui;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsOverlay extends Screen {
    private static boolean isDarkTheme = true;

    public SettingsOverlay() {
        super(Component.literal("Настройки"));
    }

    public static boolean isDarkTheme() { return isDarkTheme; }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Кнопка смены темы
        this.addRenderableWidget(Button.builder(
            Component.literal(isDarkTheme ? "Тема: Тёмная" : "Тема: Светлая"),
            b -> {
                isDarkTheme = !isDarkTheme;
                b.setMessage(Component.literal(isDarkTheme ? "Тема: Тёмная" : "Тема: Светлая"));
            }
        ).bounds(centerX - 80, centerY - 20, 160, 20).build());

        // Кнопка выхода
        this.addRenderableWidget(Button.builder(Component.literal("Выход"), b -> {
            CoolMessengerMod.CONFIG.clear();
            this.minecraft.setScreen(new LoginScreen());
        }).bounds(centerX - 80, centerY + 10, 160, 20).build());

        // Кнопка "Назад" (закрыть окно)
        this.addRenderableWidget(Button.builder(Component.literal("Назад"), b -> {
            this.minecraft.setScreen(new ChatScreen());
        }).bounds(centerX - 80, centerY + 40, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Затемнение фона
        g.fill(0, 0, this.width, this.height, 0xCC000000);
        // Мини-окно
        int w = 200;
        int h = 140;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
        g.drawCenteredString(this.font, this.title, this.width / 2, y + 10, 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
