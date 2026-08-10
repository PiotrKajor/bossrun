package pl.skynetgames.bossrun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/** /start, /reset i /bossrun. Nic wiecej nie jest potrzebne do prowadzenia wyzwania. */
public final class Cmd {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("start").executes(Cmd::start));
        dispatcher.register(Commands.literal("reset")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(Cmd::reset));
        dispatcher.register(Commands.literal("bossrun").executes(Cmd::status));
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        State s = State.get();

        if (s.allBossesDown()) {
            source.sendFailure(Component.literal("Bossowie juz padli — najpierw /reset."));
            return 0;
        }
        if (s.running) {
            source.sendFailure(Component.literal("Wyzwanie juz trwa."));
            return 0;
        }
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.isEmpty()) {
            source.sendFailure(Component.literal("Nie ma kogo zapisac — na serwerze pusto."));
            return 0;
        }

        // Sklad ustala sie TERAZ: tylko ci gracze zamrazaja gre i resetuja swiat.
        s.roster.clear();
        StringBuilder team = new StringBuilder();
        for (ServerPlayer player : online) {
            String uuid = player.getUUID().toString();
            s.roster.add(uuid);
            s.names.put(uuid, player.getGameProfile().name());
            s.deaths.putIfAbsent(uuid, 0);
            team.append(team.isEmpty() ? "" : ", ").append(player.getGameProfile().name());
        }
        s.running = true;
        s.save();
        Hud.syncDeaths(server);

        Hud.titleAll(server,
                Component.literal("▶ START").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal("podejscie #" + s.attempt + " · " + team)
                        .withStyle(ChatFormatting.WHITE),
                5, 60, 10);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("▶ Wyzwanie wystartowalo. Sklad: " + team
                                + ". Gdy ktos wyjdzie, czas i swiat staja.")
                        .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        State.get().wipe();
        Hud.syncDeaths(server);
        server.tickRateManager().setFrozen(true);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("↺ Czas i zgony wyzerowane. Swiat zostaje — /start zaczyna nowe podejscie.")
                        .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        State s = State.get();

        source.sendSuccess(() -> Component.literal("☠ Boss Run — podejscie #" + s.attempt
                + " · czas " + Hud.formatTime(s.ticks)
                + " · bossowie " + s.bosses.size() + "/" + State.BOSS_IDS.size())
                .withStyle(ChatFormatting.GOLD), false);

        if (s.roster.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Sklad nieustalony — wpisz /start.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (String uuid : s.roster) {
                String name = s.names.getOrDefault(uuid, uuid);
                boolean online = source.getServer().getPlayerList()
                        .getPlayer(java.util.UUID.fromString(uuid)) != null;
                source.sendSuccess(() -> Component.literal((online ? "● " : "○ ") + name
                                + " — zgony: " + s.deathsOf(uuid))
                        .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
            }
        }

        List<String> missing = BossRun.missingPlayers(source.getServer());
        Component line = !s.running
                ? Component.literal("⏸ czekam na /start").withStyle(ChatFormatting.YELLOW)
                : missing.isEmpty()
                ? Component.literal("▶ gra trwa").withStyle(ChatFormatting.GREEN)
                : Component.literal("⏸ zamrozone — brakuje: " + String.join(", ", missing))
                        .withStyle(ChatFormatting.RED);
        source.sendSuccess(() -> line, false);
        return 1;
    }

    private Cmd() {
    }
}
