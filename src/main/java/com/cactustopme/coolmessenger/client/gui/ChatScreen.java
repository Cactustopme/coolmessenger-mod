package com.cactustopme.coolmessenger.client.gui;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatScreen extends Screen {
    private EditBox messageInput;
    private static final List<Message> messages = new ArrayList<>();
    private static final List<ChatEntry> chats = new ArrayList<>();
    private static String currentChatUUID = null;
    private static String currentChatName = "Нет чата";
    private static boolean isSubscribed = false;
    private int scrollOffset = 0;
    private boolean autoScroll = true;

    private static class Message {
        String content;
        boolean isMine;
        String senderName;
        long timestamp;
        boolean isRead;
        Message(String content, boolean isMine, String senderName) {
            this.content = content;
            this.isMine = isMine;
            this.senderName = senderName;
            this.timestamp = System.currentTimeMillis();
            this.isRead = false;
        }
    }

    private static class ChatEntry {
        String uuid;
        String name;
        String lastMessage;
        boolean isOnline;
        ChatEntry(String uuid, String name, String lastMessage, boolean isOnline) {
            this.uuid = uuid;
            this.name = name;
            this.lastMessage = lastMessage;
            this.isOnline = isOnline;
        }
    }

    public ChatScreen() { super(Component.literal("Cool Messenger")); }

    @Override
    protected void init() {
        super.init();
        CoolMessengerMod.WS.connect();

        int panelX = 200;

        this.messageInput = new EditBox(this.font, panelX + 10, this.height - 30, this.width - panelX - 100, 20, Component.literal("Сообщение"));
        this.messageInput.setMaxLength(5000);
        this.addRenderableWidget(this.messageInput);

        this.addRenderableWidget(Button.builder(Component.literal("➤"), b -> sendMessage())
            .bounds(this.width - 80, this.height - 30, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("⚙"), b -> {
            this.minecraft.setScreen(new SettingsOverlay());
        }).bounds(this.width - 80, 10, 70, 20).build());

        if (!isSubscribed) {
            setupWsListeners();
            isSubscribed = true;
        }
    }

    private void setupWsListeners() {
        CoolMessengerMod.WS.on("CHAT_ARRAY_APPEND", this::handleChats);
        CoolMessengerMod.WS.on("CHAT_ARRAY_REPLACE", this::handleChats);
        CoolMessengerMod.WS.on("MESSAGE_ARRAY", this::handleMessages);
        CoolMessengerMod.WS.on("MESSAGE_ARRAY:CHAT_OPENED", this::handleMessages);
        CoolMessengerMod.WS.on("MESSAGE_ARRAY:OLD_MESSAGES_REQUESTED", this::handleMessages);
        CoolMessengerMod.WS.on("MESSAGE_ARRAY:MESSAGE_SENT_BY_CHAT_MEMBER", this::handleMessages);

        CoolMessengerMod.WS.on("PING", data -> {
            JsonObject pong = new JsonObject();
            pong.addProperty("action", "PONG");
            pong.addProperty("isActive", true);
            pong.addProperty("isTyping", false);
            CoolMessengerMod.WS.send(pong);
        });
    }

    private void handleChats(JsonObject data) {
        if (!data.has("chats")) return;
        JsonArray chatsJson = data.getAsJsonArray("chats");
        chats.clear();

        for (JsonElement el : chatsJson) {
            JsonObject chat = el.getAsJsonObject();
            String uuid = chat.get("UUID").getAsString();
            String name = "";
            boolean isOnline = false;

            if (chat.has("isGroupChat") && chat.get("isGroupChat").getAsBoolean()) {
                name = chat.has("name") ? chat.get("name").getAsString() : "Группа";
            } else {
                if (chat.has("members")) {
                    for (JsonElement m : chat.getAsJsonArray("members")) {
                        JsonObject member = m.getAsJsonObject();
                        String memberId = member.get("id").getAsString();
                        if (!memberId.equals(CoolMessengerMod.CONFIG.uuid)) {
                            name = member.has("username") ? member.get("username").getAsString() : "Собеседник";
                            isOnline = member.has("active") && member.get("active").getAsBoolean();
                            break;
                        }
                    }
                }
            }
            if (name.isEmpty()) name = "Чат";
            
            String lastMsg = chat.has("lastMessage") ? chat.get("lastMessage").getAsString() : "";
            chats.add(new ChatEntry(uuid, name, lastMsg, isOnline));
        }

        if (!chats.isEmpty() && currentChatUUID == null) {
            currentChatName = chats.get(0).name;
            currentChatUUID = chats.get(0).uuid;
            java.util.concurrent.CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
                JsonObject open = new JsonObject();
                open.addProperty("action", "OPEN_CHAT");
                open.addProperty("UUID", currentChatUUID);
                CoolMessengerMod.WS.send(open);
            });
        }
    }

    private void handleMessages(JsonObject data) {
        if (!data.has("messages")) return;
        JsonArray msgs = data.getAsJsonArray("messages");
        Minecraft.getInstance().execute(() -> {
            String source = data.has("source") ? data.get("source").getAsString() : "";
            if ("CHAT_OPENED".equals(source)) {
                messages.clear();
            }
            for (JsonElement el : msgs) {
                JsonObject msg = el.getAsJsonObject();
                String content = msg.has("content") ? msg.get("content").getAsString() : "";
                String sentBy = msg.has("sentBy") ? msg.get("sentBy").getAsString() : "";
                if (!content.isEmpty()) {
                    boolean isMine = sentBy.equals(CoolMessengerMod.CONFIG.uuid);
                    messages.add(new Message(content, isMine, isMine ? "Вы" : "Собеседник"));
                }
            }
            if (autoScroll) scrollOffset = 0;
        });
    }

    private void sendMessage() {
        String text = this.messageInput.getValue().trim();
        if (text.isEmpty() || currentChatUUID == null) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("action", "SEND_MESSAGE");
        JsonObject messageObj = new JsonObject();
        messageObj.addProperty("chatID", currentChatUUID);
        messageObj.addProperty("content", text);
        messageObj.addProperty("localUUID", java.util.UUID.randomUUID().toString());
        msg.add("message", messageObj);

        CoolMessengerMod.WS.send(msg);
        messages.add(new Message(text, true, "Вы"));
        this.messageInput.setValue("");
        scrollOffset = 0;
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(new Date(timestamp));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean dark = SettingsOverlay.isDarkTheme();

        int bgColor = dark ? 0xCC000000 : 0xCCFFFFFF;
        int panelColor = dark ? 0xFF1E1E1E : 0xFFE0E0E0;
        int lineColor = dark ? 0xFF444444 : 0xFFAAAAAA;
        int textColor = dark ? 0xFFFFFFFF : 0xFF000000;
        int subTextColor = dark ? 0xFFAAAAAA : 0xFF555555;
        int myBubbleColor = 0xFF2D5A8E;
        int otherBubbleColor = dark ? 0xFF2A2A2A : 0xFFD0D0D0;
        int activeChatColor = 0xFF2D5A8E;
        int inactiveChatColor = dark ? 0xFF2A2A2A : 0xFFE8E8E8;

        g.fill(0, 0, this.width, this.height, bgColor);

        int panelW = 200;
        int chatH = this.height - 60;

        // Левая панель
        g.fill(0, 0, panelW, this.height, panelColor);
        g.fill(0, 24, panelW, 25, lineColor);
        int y = 30;
        for (ChatEntry chat : chats) {
            boolean isActive = chat.uuid.equals(currentChatUUID);
            int bg = isActive ? activeChatColor : inactiveChatColor;
            g.fill(5, y, panelW - 5, y + 40, bg);
            int statusColor = chat.isOnline ? 0xFF22C55E : 0xFFEF4444;
            g.fill(10, y + 10, 18, y + 18, statusColor);
            y += 45;
        }

        // Правая панель
        int panelX = panelW;
        g.fill(panelX, 24, this.width, 25, lineColor);

        int messageHeight = 30;
        int visibleMessages = (chatH - 40) / messageHeight;
        int totalMessages = messages.size();
        int startIdx = Math.max(0, totalMessages - visibleMessages - scrollOffset);
        int endIdx = Math.min(totalMessages, startIdx + visibleMessages);

        // Плашки
        int msgY = 30;
        for (int i = startIdx; i < endIdx; i++) {
            Message msg = messages.get(i);
            int textWidth = this.font.width(msg.content);
            int bubbleWidth = Math.min(textWidth + 20, 400);
            int bubbleHeight = 25;
            if (msg.isMine) {
                int bubbleX = this.width - bubbleWidth - 20;
                g.fill(bubbleX, msgY, this.width - 20, msgY + bubbleHeight, myBubbleColor);
            } else {
                int bubbleX = panelX + 10;
                g.fill(bubbleX, msgY, bubbleX + bubbleWidth, msgY + bubbleHeight, otherBubbleColor);
            }
            msgY += messageHeight;
        }

        g.fill(panelX, this.height - 35, this.width - 85, this.height - 5, panelColor);

        // Текст: левая панель
        g.drawString(this.font, "Чаты", 10, 10, textColor, dark);
        y = 30;
        for (ChatEntry chat : chats) {
            g.drawString(this.font, chat.name, 25, y + 5, textColor, dark);
            if (!chat.lastMessage.isEmpty()) {
                String preview = chat.lastMessage.length() > 20 ? chat.lastMessage.substring(0, 20) + "..." : chat.lastMessage;
                g.drawString(this.font, preview, 25, y + 20, subTextColor, dark);
            }
            y += 45;
        }

        // Текст: правая панель
        g.drawString(this.font, currentChatName, panelX + 10, 10, textColor, dark);
        msgY = 30;
        for (int i = startIdx; i < endIdx; i++) {
            Message msg = messages.get(i);
            int textWidth = this.font.width(msg.content);
            int bubbleWidth = Math.min(textWidth + 20, 400);
            int bubbleHeight = 25;
            String time = formatTime(msg.timestamp);
            if (msg.isMine) {
                int bubbleX = this.width - bubbleWidth - 20;
                g.drawString(this.font, msg.content, bubbleX + 5, msgY + 5, 0xFFFFFFFF, true);
                g.drawString(this.font, time, bubbleX + 5, msgY + 15, 0xFFCCCCCC, true);
            } else {
                int bubbleX = panelX + 10;
                g.drawString(this.font, msg.content, bubbleX + 5, msgY + 7, textColor, dark);
            }
            msgY += messageHeight;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int chatH = this.height - 60;
        int messageHeight = 30;
        int visibleMessages = (chatH - 40) / messageHeight;
        int maxScroll = Math.max(0, messages.size() - visibleMessages);

        if (scrollY > 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            autoScroll = false;
        } else if (scrollY < 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            if (scrollOffset == 0) autoScroll = true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            int panelW = 200;
            int y = 30;
            double mouseX = event.x();
            double mouseY = event.y();
            for (ChatEntry chat : chats) {
                if (mouseX >= 5 && mouseX <= panelW - 5 && mouseY >= y && mouseY <= y + 40) {
                    currentChatUUID = chat.uuid;
                    currentChatName = chat.name;
                    messages.clear();
                    scrollOffset = 0;
                    autoScroll = true;
                    JsonObject open = new JsonObject();
                    open.addProperty("action", "OPEN_CHAT");
                    open.addProperty("UUID", chat.uuid);
                    CoolMessengerMod.WS.send(open);
                    return true;
                }
                y += 45;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.key() == 257 || keyEvent.key() == 335) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyEvent);
    }
}
