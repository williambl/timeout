package com.williambl.timeout;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.williambl.timeout.mixin.ServerConfigEntryAccessor;
import com.williambl.timeout.mixin.ServerConfigListAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public class TimeoutManager extends ServerConfigList<GameProfile, TimeoutEntry> implements ServerTickEvents.StartTick {
    private LocalDateTime lastPlaytimeUpdate = LocalDateTime.now();

    public TimeoutManager(File file) {
        super(file);
    }

    @Override
    public void load() throws IOException {
        if (this.getFile().exists()) {
            BufferedReader bufferedReader = Files.newReader(this.getFile(), StandardCharsets.UTF_8);

            try {
                JsonObject jObject = getGson().fromJson(bufferedReader, JsonObject.class);
                JsonArray jsonArray = jObject.getAsJsonArray("Entries");
                this.getMap().clear();

                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonObject = JsonHelper.asObject(jsonElement, "entry");
                    TimeoutEntry serverConfigEntry = this.fromJson(jsonObject);
                    //noinspection unchecked
                    if (((ServerConfigEntryAccessor<GameProfile>)serverConfigEntry).invokeGetKey() != null) {
                        //noinspection unchecked
                        this.getMap().put(this.toString(((ServerConfigEntryAccessor<GameProfile>)serverConfigEntry).invokeGetKey()), serverConfigEntry);
                    }
                }
                lastPlaytimeUpdate = LocalDateTime.parse(jObject.get("LastPlaytimeUpdate").getAsString());

            } catch (Throwable catchEmAll) {
                try {
                    bufferedReader.close();
                } catch (Throwable var7) {
                    catchEmAll.addSuppressed(var7);
                }

                throw catchEmAll;
            }
            bufferedReader.close();
        }
    }

    @Override
    public void save() throws IOException {
        JsonArray jsonArray = new JsonArray();
        getMap().values().stream()
                .map(entry -> Util.make(new JsonObject(), entry::fromJson))
                .forEach(jsonArray::add);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("Entries", jsonArray);
        jsonObject.addProperty("LastPlaytimeUpdate", lastPlaytimeUpdate.toString());

        BufferedWriter bufferedWriter = Files.newWriter(this.getFile(), StandardCharsets.UTF_8);

        try {
            getGson().toJson(jsonObject, bufferedWriter);
        } catch (Throwable catchEmAll) {
            try {
                bufferedWriter.close();
            } catch (Throwable var5) {
                catchEmAll.addSuppressed(var5);
            }

            throw catchEmAll;
        }

        if (bufferedWriter != null) {
            bufferedWriter.close();
        }
    }

    @Override
    protected TimeoutEntry fromJson(JsonObject json) {
        return new TimeoutEntry(json);
    }

    @Override
    protected String toString(GameProfile gameProfile) {
        return gameProfile.getId().toString();
    }

    public TimeoutEntry getOrCreate(ServerPlayerEntity player) {
        TimeoutEntry entry = get(player.getGameProfile());
        if (entry == null) {
            entry = new TimeoutEntry(player.getGameProfile(), LocalDateTime.now(), getCurrentPlaytime(player));
            add(entry);
        }
        return entry;
    }

    public void checkPlaytimes(MinecraftServer server) {
        server.getPlayerManager().getPlayerList().stream().filter(this::playtimeOverThreshold).collect(Collectors.toList()).forEach(this::ban);
    }

    public boolean playtimeOverThreshold(ServerPlayerEntity player) {
        return getCurrentPlaytime(player) - getOrCreate(player).getLastPlaytime() > 20 * 3600 * player.world.getGameRules().getInt(Timeout.MAX_HOURS);
    }

    public void ban(ServerPlayerEntity player) {
        BannedPlayerList bannedPlayers = player.server.getPlayerManager().getUserBanList();
        if (!bannedPlayers.contains(player.getGameProfile())) {
            bannedPlayers.add(new BannedPlayerEntry(player.getGameProfile(), Calendar.getInstance().getTime(), "timeout", Date.from(lastPlaytimeUpdate.plusWeeks(1).toInstant(ZoneOffset.UTC)), "Time out, wait until next week!"));
            player.networkHandler.disconnect(new TranslatableText("multiplayer.disconnect.banned"));
        }
    }

    public void updatePlaytimes(MinecraftServer server) {
        LocalDateTime now = LocalDateTime.now();
        if (lastPlaytimeUpdate.isBefore(now.minusWeeks(1))) {
            lastPlaytimeUpdate = now;
            server.getPlayerManager().getPlayerList().forEach(player -> {
                        remove(player.getGameProfile());
                        add(new TimeoutEntry(player.getGameProfile(), lastPlaytimeUpdate, getCurrentPlaytime(player)));
                    }
            );
        }

        /*
        for (TimeoutEntry v : values()) {
            System.out.println(v.getLastUpdated());
            System.out.println(lastPlaytimeUpdate);
            System.out.println(v.getLastUpdated().isBefore(lastPlaytimeUpdate));
        }*/

        //noinspection unchecked
        var entries = values().stream()
                .filter(entry -> entry.getLastUpdated().isBefore(lastPlaytimeUpdate))
                .map(entry -> ((ServerConfigEntryAccessor<GameProfile>)entry).invokeGetKey().getId())
                .map(server.getPlayerManager()::getPlayer)
                .filter(Objects::nonNull)
                .map(player -> new TimeoutEntry(player.getGameProfile(), lastPlaytimeUpdate, getCurrentPlaytime(player)))
                .collect(Collectors.toList());

        entries.forEach(entry -> {
            //noinspection unchecked
            this.remove(((ServerConfigEntryAccessor<GameProfile>)entry).invokeGetKey());
        });
        entries.forEach(this::add);
    }

    private Map<String, TimeoutEntry> getMap() {
        //noinspection unchecked
        return ((ServerConfigListAccessor<GameProfile, TimeoutEntry>)this).getMap();
    }

    private static Gson getGson() {
        return ServerConfigListAccessor.getGSON();
    }

    static int getCurrentPlaytime(ServerPlayerEntity player) {
        return player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
    }

    @Override
    public void onStartTick(MinecraftServer server) {
        updatePlaytimes(server);
        checkPlaytimes(server);
    }
}
