package com.cactustopme.coolmessenger.client.gui;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LoginScreen extends Screen {
    private EditBox usernameField;
    private EditBox passwordField;
    private Button loginButton;
    private String errorMessage = "";

    public LoginScreen() {
        super(Component.literal("Cool Messenger - Вход"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.usernameField = new EditBox(this.font, centerX - 100, centerY - 40, 200, 20, Component.literal("Логин"));
        this.usernameField.setMaxLength(30);
        this.usernameField.setHint(Component.literal("Имя пользователя"));
        this.addRenderableWidget(this.usernameField);

        this.passwordField = new EditBox(this.font, centerX - 100, centerY, 200, 20, Component.literal("Пароль"));
        this.passwordField.setMaxLength(50);
        this.passwordField.setHint(Component.literal("Пароль"));
        this.passwordField.addFormatter((text, cursorPos) -> Component.literal("*".repeat(text.length())).getVisualOrderText());
        this.addRenderableWidget(this.passwordField);

        this.loginButton = Button.builder(Component.literal("Войти"), b -> attemptLogin())
                .bounds(centerX - 100, centerY + 40, 200, 20)
                .build();
        this.addRenderableWidget(this.loginButton);
    }

    private void attemptLogin() {
        String user = this.usernameField.getValue();
        String pass = this.passwordField.getValue();
        if (user.isEmpty() || pass.isEmpty()) {
            this.errorMessage = "Заполните все поля!";
            return;
        }

        this.loginButton.active = false;
        this.errorMessage = "Подключение...";

        CoolMessengerMod.API.userLogin(user, pass).thenAccept(resp -> {
            this.minecraft.execute(() -> {
                if (resp.has("result") && "SUCCESS".equals(resp.get("result").getAsString())) {
                    var cfg = CoolMessengerMod.CONFIG;
                    cfg.uuid = resp.get("UUID").getAsString();
                    cfg.sessionID = resp.get("session").getAsString();
                    cfg.refreshToken = resp.has("refreshToken") ? resp.get("refreshToken").getAsString() : null;
                    cfg.deviceID = resp.has("deviceID") ? resp.get("deviceID").getAsString() : null;
                    cfg.username = user;
                    cfg.save();

                    CoolMessengerMod.WS.connect();
                    this.minecraft.setScreen(new ChatScreen());
                } else {
                    this.loginButton.active = true;
                    this.errorMessage = "Ошибка: " + (resp.has("result") ? resp.get("result").getAsString() : "Неизвестно");
                }
            });
        }).exceptionally(err -> {
            this.minecraft.execute(() -> {
                this.loginButton.active = true;
                this.errorMessage = "Сетевая ошибка";
                CoolMessengerMod.LOG.error("Login error", err);
            });
            return null;
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Рисуем полупрозрачный темно-синий фон на весь экран (ARGB: FF - альфа, 1A - красный, 2B - зеленый, 4A - синий)
        
        
        // 2. Отрисовка виджетов и текста
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 70, 0xFFFFFF);
        
        if (!this.errorMessage.isEmpty()) {
            int color = this.errorMessage.equals("Подключение...") ? 0xFFFF00 : 0xFF5555;
            guiGraphics.drawCenteredString(this.font, this.errorMessage, this.width / 2, this.height / 2 + 70, color);
        }
    }
}
