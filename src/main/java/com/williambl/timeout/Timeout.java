package com.williambl.timeout;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.LiteralText;
import net.minecraft.world.GameRules;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Timeout implements DedicatedServerModInitializer {

    public static final GameRules.Key<GameRules.IntRule> MAX_HOURS
            = GameRuleRegistry.register("maxHoursPerWeek", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(7));

    private static final File TIMEOUT_FILE = new File("timeouts.json");
    private TimeoutManager timeouts;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            timeouts = new TimeoutManager(TIMEOUT_FILE);
            ServerTickEvents.START_SERVER_TICK.register(timeouts);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                timeouts.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> pardonTimedOutBans(server.getPlayerManager().getUserBanList()));
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            if (dedicated) {
                dispatcher.register(literal("checktime")
                        .executes(context -> runCommand(context, context.getSource().getPlayer()))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> runCommand(context, EntityArgumentType.getPlayer(context, "player"))))
                );
            }
        });
    }

    public void pardonTimedOutBans(BannedPlayerList bannedPlayers) {
        Date now = Calendar.getInstance().getTime();
        List<BannedPlayerEntry> toRemove = new ArrayList<>();
        bannedPlayers.values().stream()
                .filter(entry -> entry.getExpiryDate().before(now))
                .forEach(toRemove::add);
        toRemove.forEach(bannedPlayers::remove);
    }

    private int runCommand(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        if (player == null) {
            context.getSource().sendError(new LiteralText("You must be a player to run this!"));
            return 0;
        } else {
            int hours = (player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME))
                    - timeouts.get(player.getGameProfile()).getLastPlaytime())/(20*3600);
            context.getSource().sendFeedback(new LiteralText("You have played " +
                            hours + " / "
                            + context.getSource().getMinecraftServer().getGameRules().getInt(MAX_HOURS)
                            + " hours this week."),
                    false
            );
            return hours;
        }
    }
}
