package dev.kajor.bossrun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** /start, /reset i /bossrun. Nic wiecej nie jest potrzebne do prowadzenia wyzwania. */
public final class Cmd {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("start").executes(Cmd::start));
        dispatcher.register(Commands.literal("reset")
                .requires(Compat::operator)
                .executes(Cmd::reset));
        dispatcher.register(Commands.literal("bossrun").executes(Cmd::status));
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        State s = State.get();

        if (s.allGoalsDone()) {
            source.sendFailure(Component.literal(Msg.of("start.already_complete")));
            return 0;
        }
        if (s.running) {
            source.sendFailure(Component.literal(Msg.of("start.already_running")));
            return 0;
        }
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.isEmpty()) {
            source.sendFailure(Component.literal(Msg.of("start.nobody")));
            return 0;
        }

        // Sklad ustala sie TERAZ: tylko ci gracze zamrazaja gre i resetuja swiat.
        s.roster.clear();
        StringBuilder team = new StringBuilder();
        for (ServerPlayer player : online) {
            String uuid = player.getUUID().toString();
            s.roster.add(uuid);
            s.names.put(uuid, Compat.nick(player));
            s.deaths.putIfAbsent(uuid, 0);
            team.append(team.isEmpty() ? "" : ", ").append(Compat.nick(player));
        }
        s.running = true;
        s.save();
        Hud.syncDeaths(server);

        Hud.titleAll(server,
                Component.literal(Msg.of("start.title")).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal(Msg.of("start.subtitle", s.attempt, team))
                        .withStyle(ChatFormatting.WHITE),
                5, 60, 10);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(Msg.of("start.chat", team)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        State.get().wipe();
        Hud.syncDeaths(server);
        Compat.zamrozenie(server, true);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(Msg.of("reset.chat")).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        State s = State.get();

        source.sendSuccess(() -> Component.literal(Msg.of("cmd.header", s.attempt,
                Hud.formatTime(s.ticks), s.done.size(), Config.get().goals.size()))
                .withStyle(ChatFormatting.GOLD), false);

        // Cele wypisujemy zawsze - inaczej gracz nie ma jak sprawdzic, co wlasciwie
        // wpisano w configu tego serwera.
        for (Goal goal : Config.get().goals) {
            boolean done = s.isDone(goal.target());
            String etykieta = goal.label();
            if (!done && goal.amount() > 1) {
                etykieta += " (" + s.progressOf(goal.target()) + "/" + goal.amount() + ")";
            }
            String label = etykieta;
            source.sendSuccess(() -> Component.literal(
                            Msg.of("cmd.goal_line", done ? "✔" : "✖", label))
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        }

        if (s.roster.isEmpty()) {
            source.sendSuccess(() -> Component.literal(Msg.of("cmd.no_roster"))
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (String uuid : s.roster) {
                String name = s.names.getOrDefault(uuid, uuid);
                boolean online = source.getServer().getPlayerList()
                        .getPlayer(java.util.UUID.fromString(uuid)) != null;
                source.sendSuccess(() -> Component.literal(Msg.of("cmd.player",
                                online ? "● " : "○ ", name, s.deathsOf(uuid)))
                        .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
            }
        }

        List<String> missing = BossRun.missingPlayers(source.getServer());
        Component line = !s.running
                ? Component.literal(Msg.of("status.await_start")).withStyle(ChatFormatting.YELLOW)
                : missing.isEmpty()
                ? Component.literal(Msg.of("status.running")).withStyle(ChatFormatting.GREEN)
                : Component.literal(Msg.of("status.frozen", String.join(", ", missing)))
                        .withStyle(ChatFormatting.RED);
        source.sendSuccess(() -> line, false);
        return 1;
    }

    private Cmd() {
    }
}
