package com.williambl.timeout;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.world.GameRules;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Timeout implements ModInitializer {

    public static final GameRules.Key<GameRules.IntRule> MAX_HOURS
            = GameRuleRegistry.register("maxHoursPerWeek", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(7));

    private static final File TIMEOUT_FILE = new File("timeouts.json");
    private TimeoutManager timeouts;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            timeouts = new TimeoutManager(TIMEOUT_FILE);
            ServerTickEvents.START_SERVER_TICK.register(timeouts);
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> pardonTimedOutBans(server.getPlayerManager().getUserBanList()));
    }

    public void pardonTimedOutBans(BannedPlayerList bannedPlayers) {
        Date now = Calendar.getInstance().getTime();
        List<BannedPlayerEntry> toRemove = new ArrayList<>();
        bannedPlayers.values().stream()
                .filter(entry -> entry.getExpiryDate().before(now))
                .forEach(toRemove::add);
        toRemove.forEach(bannedPlayers::remove);
    }
}
