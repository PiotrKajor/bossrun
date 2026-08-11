package pl.skynetgames.bossrun;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pauza, która naprawdę zatrzymuje graczy.
 *
 * <p>Samo zamrożenie świata (`/tick freeze`) zatrzymuje moby, rośliny i czas, ale nie
 * gracza: ten dalej chodzi, kopie i zbiera. Wyzwanie liczy się na czas, więc dopóki
 * ktoś ze składu jest offline, reszta nie może robić NIC — inaczej pauza byłaby
 * darmowym czasem na farmienie.
 *
 * <p>Dwie warstwy: kotwica (gracz wraca na miejsce, z którego chce się ruszyć) i twarde
 * odrzucanie interakcji. Sama kotwica nie wystarcza, bo kopać można stojąc w miejscu.
 */
public final class Freeze {

    /** Gdzie stał gracz w chwili zamrożenia. */
    private static final Map<UUID, Vec3> ANCHORS = new HashMap<>();
    private static boolean active;

    /** Dalej niż to od kotwicy = próba ruchu. Luz na drgania pozycji przy stawaniu. */
    private static final double SLACK_SQR = 0.02D;

    public static boolean active() {
        return active;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> deny());
        UseItemCallback.EVENT.register((player, level, hand) -> deny());
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> deny());
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> deny());
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> deny());
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !active);
    }

    private static InteractionResult deny() {
        return active ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    /**
     * Trzyma wszystkich w miejscu. Wołane co tick, dopóki gra ma stać.
     *
     * @param refreshEffects czy odnowić spowolnienie (raz na sekundę wystarczy —
     *                       każde nałożenie efektu to pakiet do klienta)
     */
    public static void hold(MinecraftServer server, boolean refreshEffects) {
        active = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 anchor = ANCHORS.computeIfAbsent(player.getUUID(), uuid -> player.position());

            if (player.distanceToSqr(anchor) > SLACK_SQR) {
                player.connection.teleport(anchor.x, anchor.y, anchor.z,
                        player.getYRot(), player.getXRot());
            }

            // Pauza w powietrzu zabijala: klient dalej spada, serwer liczy kazdy taki
            // ruch do fallDistance, kotwica ciagnie gracza z powrotem w gore - i licznik
            // rosnie przez cala pauze. Po odmrozeniu przy pierwszym dotknieciu ziemi
            // schodzilo tyle zycia, ile trwalo czekanie. Rachunek zerujemy co tick,
            // wiec po pauzie gracz spada normalnie: z wysokosci, na ktorej stanal.
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);

            // Spowolnienie samo w sobie niczego nie gwarantuje (kotwica gwarantuje),
            // ale bez niego klient wciąż próbuje iść i gracz widzi szarpanie.
            if (refreshEffects) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255,
                        false, false, false));
            }

            // Skrzynia czy stół rzemieślniczy otwarte tuż przed pauzą byłyby luką:
            // przekładanie rzeczy to też granie.
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
        }
    }

    /** Koniec pauzy: kotwice znikają, spowolnienie schodzi. */
    public static void release(MinecraftServer server) {
        if (!active) return;
        active = false;
        ANCHORS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeEffect(MobEffects.SLOWNESS);
            // Ostatni tick pauzy zdazyl juz cos dolozyc - gra rusza z czystym licznikiem.
            player.resetFallDistance();
        }
    }

    /** Gracz wyszedł — jego kotwica jest już bez znaczenia. */
    public static void forget(UUID uuid) {
        ANCHORS.remove(uuid);
    }

    private Freeze() {
    }
}
