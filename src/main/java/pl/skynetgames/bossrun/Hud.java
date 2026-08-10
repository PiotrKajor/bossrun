package pl.skynetgames.bossrun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.List;
import java.util.Map;

/**
 * Wszystko, co gracz widzi: liczba zgonow na TAB-ie, naglowek z czasem i postepem,
 * wielkie napisy na srodku ekranu.
 *
 * Zgony pokazuje wanilkowy scoreboard w slocie LIST - liczba laduje obok nicka,
 * a glowke gracza TAB rysuje sam. Zero moda po stronie klienta.
 */
public final class Hud {

    private static final String OBJECTIVE = "bossrun_zgony";

    /** Ladne nazwy bossow - id z rejestru nie nadaje sie na ekran. */
    private static final Map<String, String> BOSS_NAMES = Map.of(
            "minecraft:ender_dragon", "Ender Dragon",
            "minecraft:wither", "Wither",
            "minecraft:elder_guardian", "Elder Guardian",
            "minecraft:warden", "Warden");

    /** Scoreboard siedzi w folderze swiata, wiec po kazdym resecie trzeba go zbudowac od nowa. */
    public static void setup(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                    Component.literal("☠").withStyle(ChatFormatting.RED),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        sb.setDisplayObjective(DisplaySlot.LIST, obj);
    }

    public static void syncDeaths(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) syncDeaths(player);
    }

    public static void syncDeaths(ServerPlayer player) {
        Scoreboard sb = player.level().getServer().getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE);
        if (obj == null) return;
        sb.getOrCreatePlayerScore(player, obj).set(State.get().deathsOf(player.getUUID().toString()));
    }

    /** Naglowek i stopka TAB-a. Wysylane raz na sekunde - czesciej nikt nie zauwazy. */
    public static void pushTabList(MinecraftServer server, Component status) {
        State s = State.get();

        Component header = Component.empty()
                .append(Component.literal("☠ BOSS RUN HARDCORE ☠\n")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal("podejscie #" + s.attempt + "  ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("⏱ " + formatTime(s.ticks))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        MutableComponent footer = Component.literal("\n");
        for (String id : State.BOSS_IDS) {
            boolean down = s.bosses.contains(id);
            footer.append(Component.literal((down ? "✔ " : "✖ ") + BOSS_NAMES.get(id) + "   ")
                    .withStyle(down ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        footer.append(Component.literal("\n")).append(status);
        if (s.bestTicks > 0) {
            footer.append(Component.literal("\n★ rekord: " + formatTime(s.bestTicks))
                    .withStyle(ChatFormatting.YELLOW));
        }

        var packet = new ClientboundTabListPacket(header, footer);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.connection.send(packet);
    }

    /** Wielki napis na srodku ekranu u wszystkich. Czasy w tickach. */
    public static void titleAll(MinecraftServer server, Component title, Component subtitle,
                                int fadeIn, int stay, int fadeOut) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        var anim = new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
        var mainLine = new ClientboundSetTitleTextPacket(title);
        var subLine = new ClientboundSetSubtitleTextPacket(subtitle);
        for (ServerPlayer player : players) {
            player.connection.send(anim);
            player.connection.send(subLine);
            player.connection.send(mainLine);
        }
    }

    /** mm:ss albo h:mm:ss - bez milisekund, nikt ich nie czyta w biegu. */
    public static String formatTime(long ticks) {
        long total = ticks / 20L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long sec = total % 60L;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    private Hud() {
    }
}
