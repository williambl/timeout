package com.williambl.timeout;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.TranslatableText;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Collectors;

public class TimeoutManager extends ServerConfigList<GameProfile, TimeoutEntry> implements ServerTickEvents.StartTick {
    private LocalDateTime lastPlaytimeUpdate = LocalDateTime.now();

    public TimeoutManager(File file) {
        super(file);
    }

    @Override
    protected ServerConfigEntry<GameProfile> fromJson(JsonObject json) {
        return new TimeoutEntry(json);
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
            server.getPlayerManager().getPlayerList().forEach(player ->
                    add(new TimeoutEntry(player.getGameProfile(), lastPlaytimeUpdate, getCurrentPlaytime(player)))
            );
        }
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
