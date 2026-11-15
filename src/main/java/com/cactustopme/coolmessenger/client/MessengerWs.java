package com.cactustopme.coolmessenger.client;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MessengerWs {
    public static final String WS_URL = "wss://coolmessenger.ru:8080/ws";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final ConcurrentLinkedQueue<JsonObject> incoming = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<JsonObject>>> handlers = new ConcurrentHashMap<>();

    private volatile WebSocket ws;
    private volatile boolean isConnecting = false;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;

    public void connect() {
        if (ws != null || isConnecting) return;
        isConnecting = true;
        String sessionID = CoolMessengerMod.CONFIG.sessionID;
        if (sessionID == null) {
            CoolMessengerMod.LOG.warn("[WS] Нет sessionID, подключение отменено");
            return;
        }
        shouldReconnect = true;
        HTTP.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create(WS_URL), new Listener())
            .thenAccept(socket -> {
                ws = socket;
                CoolMessengerMod.LOG.info("[WSDEBUG] Подключено, отправляю CONFIRM");
                JsonObject confirm = new JsonObject();
                confirm.addProperty("action", "CONFIRM");
                confirm.addProperty("sessionID", sessionID);
                String confirmStr = GSON.toJson(confirm);
                CoolMessengerMod.LOG.info("[WSDEBUG] Отправка CONFIRM: " + confirmStr);
                socket.sendText(confirmStr, true);
                reconnectAttempts = 0;
                isConnecting = false;
            })
            .exceptionally(err -> {
                CoolMessengerMod.LOG.warn("[WS] Ошибка подключения", err);
                isConnecting = false;
                scheduleReconnect();
                return null;
            });
    }

    public void send(JsonObject obj) {
        WebSocket s = ws;
        if (s != null && !s.isOutputClosed()) {
            s.sendText(GSON.toJson(obj), true);
        } else {
            CoolMessengerMod.LOG.warn("[WS] Не подключено, сообщение потеряно: " + obj);
        }
    }

    public void tick() {
        JsonObject msg;
        while ((msg = incoming.poll()) != null) {
            dispatch(msg);
        }
    }

    private void dispatch(JsonObject msg) {
        // Отладочный триггер для всех сообщений
        fire("ALL_MESSAGES", msg);
        
        String type = msg.has("type") ? msg.get("type").getAsString() : null;
        
        // ВАЖНО: отвечаем на PING ВСЕГДА, даже если GUI закрыт
        if ("PING".equals(type)) {
            JsonObject pong = new JsonObject();
            pong.addProperty("action", "PONG");
            pong.addProperty("isActive", true);
            pong.addProperty("isTyping", false);
            send(pong);
            return;
        }

        if ("PING".equals(type)) {
            JsonObject pong = new JsonObject();
            pong.addProperty("action", "PONG");
            pong.addProperty("isActive", true);
            pong.addProperty("isTyping", false);
            send(pong);
            return;
        }
        if ("SESSION_EXPIRED".equals(type)) {
            handleSessionExpired();
            return;
        }
        if (type != null) {
            fire(type, msg);
            if ("MESSAGE_ARRAY".equals(type)) {
                String source = msg.has("source") ? msg.get("source").getAsString() : "";
                fire("MESSAGE_ARRAY:" + source, msg);
            }
        }
        if (msg.has("result")) {
            fire("result", msg);
        }
    }

    private void handleSessionExpired() {
        CoolMessengerMod.LOG.warn("[WS] SESSION_EXPIRED, обновляем сессию");
        var cfg = CoolMessengerMod.CONFIG;
        if (cfg.refreshToken == null || cfg.deviceID == null) {
            cfg.clear();
            return;
        }
        MessengerApiRefresher.refresh(cfg, () -> {
            closeQuietly();
            connect();
        });
    }

    public void on(String type, Consumer<JsonObject> handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    private void fire(String type, JsonObject msg) {
        var list = handlers.get(type);
        if (list != null) {
            for (var h : list) {
                try { h.accept(msg); }
                catch (Exception e) { CoolMessengerMod.LOG.error("[WS] handler " + type, e); }
            }
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        if (reconnectAttempts >= 3) {
            CoolMessengerMod.LOG.warn("[WS] Слишком много попыток, рефрешим сессию");
            handleSessionExpired();
            return;
        }
        reconnectAttempts++;
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
            .execute(this::connect);
    }

    public void disconnect() {
        shouldReconnect = false;
        closeQuietly();
    }

    private void closeQuietly() {
        WebSocket s = ws;
        ws = null;
        if (s != null) {
            try { s.sendClose(4001, "manual"); } catch (Exception ignored) {}
        }
    }

    private class Listener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            CoolMessengerMod.LOG.info("[WSDEBUG] onOpen вызван, запрашиваю данные");
            webSocket.request(1);
        }
        private final StringBuilder buf = new StringBuilder();

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                try {
                    String text = buf.toString();
                    CoolMessengerMod.LOG.info("[WSDEBUG] Входящее: " + text);
                    incoming.add(JsonParser.parseString(text).getAsJsonObject());
                } catch (Exception e) {
                    CoolMessengerMod.LOG.warn("[WS] Ошибка парсинга", e);
                }
                buf.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            ws = null;
            CoolMessengerMod.LOG.info("[WS] Закрыто: " + statusCode + " " + reason);
            if (shouldReconnect && statusCode != 4001) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            ws = null;
            CoolMessengerMod.LOG.warn("[WS] Ошибка", error);
            if (shouldReconnect) scheduleReconnect();
        }
    }
}
