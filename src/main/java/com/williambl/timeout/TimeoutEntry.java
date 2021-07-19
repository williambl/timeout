package com.williambl.timeout;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.williambl.timeout.mixin.ServerConfigEntryAccessor;
import net.minecraft.server.ServerConfigEntry;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public class TimeoutEntry extends ServerConfigEntry<GameProfile> {
    private final LocalDateTime lastUpdated;
    private final int lastPlaytime;

    public TimeoutEntry(@Nullable GameProfile key, LocalDateTime lastUpdated, int lastPlaytime) {
        super(key);
        this.lastUpdated = lastUpdated;
        this.lastPlaytime = lastPlaytime;
    }

    public TimeoutEntry(JsonObject json) {
        super(profileFromJson(json));
        lastUpdated = LocalDateTime.parse(json.get("lastUpdated").getAsString());
        lastPlaytime = json.get("lastPlaytime").getAsInt();
    }

    @Override
    protected void fromJson(JsonObject json) {
        //noinspection unchecked
        json.addProperty("uuid", ((ServerConfigEntryAccessor<GameProfile>)this).invokeGetKey().getId().toString());
        //noinspection unchecked
        json.addProperty("name", ((ServerConfigEntryAccessor<GameProfile>)this).invokeGetKey().getName());
        json.addProperty("lastUpdated", lastUpdated.toString());
        json.addProperty("lastPlaytime", lastPlaytime);
    }

    private static GameProfile profileFromJson(JsonObject json) {
        if (json.has("uuid") && json.has("name")) {
            String string = json.get("uuid").getAsString();

            UUID uUID2;
            try {
                uUID2 = UUID.fromString(string);
            } catch (Throwable var4) {
                return null;
            }

            return new GameProfile(uUID2, json.get("name").getAsString());
        } else {
            return null;
        }
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public int getLastPlaytime() {
        return lastPlaytime;
    }
}
