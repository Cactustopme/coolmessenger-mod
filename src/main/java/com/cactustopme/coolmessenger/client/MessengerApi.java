package com.cactustopme.coolmessenger.client;

import com.cactustopme.coolmessenger.CoolMessengerMod;
import com.cactustopme.coolmessenger.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class MessengerApi {
    public static final String API_BASE = "https://coolmessenger.ru:8080";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private CompletableFuture<JsonObject> request(String endpoint, String method, JsonObject body, boolean auth) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API_BASE + endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");

            ModConfig cfg = CoolMessengerMod.CONFIG;
            if (auth && cfg.sessionID != null) {
                b.header("Authorization", cfg.sessionID);
            }

            if (body != null) {
                b.method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }

            return HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> JsonParser.parseString(resp.body()).getAsJsonObject());
        } catch (Exception e) {
            CompletableFuture<JsonObject> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public CompletableFuture<JsonObject> userLogin(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return request("/userlogin", "POST", body, false);
    }

    public CompletableFuture<JsonObject> isSessionValid() {
        return request("/is_session_valid", "GET", null, true);
    }

    public CompletableFuture<JsonObject> refreshSession(String deviceID, String refreshToken) {
        JsonObject body = new JsonObject();
        body.addProperty("deviceID", deviceID);
        body.addProperty("refreshToken", refreshToken);
        return request("/refresh_session", "POST", body, false);
    }

    public CompletableFuture<JsonObject> getThisUserData() {
        return request("/get_this_user_data", "GET", null, true);
    }

    public CompletableFuture<JsonObject> searchUser(String username) {
        return request("/getuser?username=" + urlEncode(username), "GET", null, true);
    }

    public CompletableFuture<JsonObject> newDirectChat(String targetUUID) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUUID", targetUUID);
        return request("/new_direct_chat", "POST", body, true);
    }

    public CompletableFuture<JsonObject> newGroupChat(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        return request("/new_group_chat", "POST", body, true);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
