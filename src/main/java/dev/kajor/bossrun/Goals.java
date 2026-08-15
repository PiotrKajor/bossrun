package dev.kajor.bossrun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

import java.util.UUID;

/**
 * Odhaczanie celow.
 *
 * Zabicia lapiemy eventem, bo trupa nie da sie znalezc po fakcie. Reszte - posiadanie
 * przedmiotu, wymiar, osiagniecie - skanujemy raz na sekunde; czesciej nie ma sensu,
 * a przy kazdym ticku liczylibysmy ekwipunek dwadziescia razy szybciej bez zadnego zysku.
 *
 * Liczy sie caly sklad: wyzwanie jest druzynowe, wiec elytra u jednego gracza zalicza
 * cel calej druzynie.
 */
public final class Goals {

    private static final int SCAN_TICKS = 20;
    private static int counter = 0;

    /** @return true, jesli to zabicie domknelo jakis cel (wtedy warto sprawdzic zwyciestwo). */
    public static boolean onKill(MinecraftServer server, String entityId) {
        State s = State.get();
        boolean domkniete = false;
        for (Goal goal : Config.get().goals) {
            if (goal.type() != Goal.Type.KILL || !goal.target().equals(entityId)) continue;
            if (s.isDone(goal.target())) continue;

            int n = s.addProgress(goal.target());
            if (n >= goal.amount()) {
                announce(server, goal);
                domkniete = true;
            } else {
                Hud.actionBarAll(server, Component.literal(
                        Msg.of("goal.progress", goal.label(), n, goal.amount()))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        return domkniete;
    }

    /** @return true, jesli skan cokolwiek domknal. */
    public static boolean scan(MinecraftServer server) {
        if (++counter < SCAN_TICKS) return false;
        counter = 0;

        State s = State.get();
        boolean domkniete = false;
        for (Goal goal : Config.get().goals) {
            if (goal.type() == Goal.Type.KILL || s.isDone(goal.target())) continue;
            for (String uuid : s.roster) {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(uuid));
                if (player != null && spelniony(goal, player)) {
                    s.addDone(goal.target());
                    announce(server, goal);
                    domkniete = true;
                    break;
                }
            }
        }
        return domkniete;
    }

    private static boolean spelniony(Goal goal, ServerPlayer player) {
        return switch (goal.type()) {
            case HAVE -> policzPrzedmiot(player, goal.target()) >= goal.amount();
            case REACH -> Compat.wymiar(player).equals(goal.target());
            case ADVANCEMENT -> maOsiagniecie(player, goal.target());
            case KILL -> false;   // liczone eventem, nie skanowaniem
        };
    }

    private static int policzPrzedmiot(ServerPlayer player, String itemId) {
        Item item = Compat.przedmiot(itemId);
        if (item == null) return 0;

        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) n += stack.getCount();
        }
        return n;
    }

    /** Osiagniecia sa publicznym API serwera, wiec obchodzimy sie bez mixinu w PlayerAdvancements. */
    private static boolean maOsiagniecie(ServerPlayer player, String advancementId) {
        Boolean ma = Compat.maOsiagniecie(player, advancementId);
        if (ma == null) {
            BossRun.LOG.warn("Cel wskazuje na nieznane osiagniecie: {}", advancementId);
            return false;
        }
        return ma;
    }

    private static void announce(MinecraftServer server, Goal goal) {
        State s = State.get();
        s.addDone(goal.target());

        Hud.titleAll(server,
                Component.literal(Msg.of("goal.done.title"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(Msg.of("goal.done.subtitle",
                                s.done.size(), Config.get().goals.size(), goal.label()))
                        .withStyle(ChatFormatting.YELLOW),
                5, 50, 15);
        BossRun.playForAll(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
    }

    private Goals() {
    }
}
