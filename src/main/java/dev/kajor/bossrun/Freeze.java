package dev.kajor.bossrun;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

    /** Gdzie stał gracz w chwili zamrożenia — z wymiarem, bo same współrzędne kłamią. */
    private record Anchor(ResourceKey<Level> dimension, Vec3 pos) {
    }

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();
    private static boolean active;

    /** Dalej niż to od kotwicy = próba ruchu. Luz na drgania pozycji przy stawaniu. */
    private static final double SLACK_SQR = 0.02D;

    public static boolean active() {
        return active;
    }

    public static void register() {
        // Wanilkowe /tick freeze NIE zamraza graczy (TickRateManager.isEntityFrozen robi
        // dla nich wyjatek), wiec przez cala pauze leci im utoniecie, ogien, lawa, glod,
        // trucizna i marzniecie. W hardcore czekanie na kolege konczylo run. Moby i tak
        // stoja, wiec tarcza nikomu nie daje przewagi - odbiera tylko smierc za czekanie.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> !active || !(entity instanceof ServerPlayer));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> deny());
        Compat.blokujUzyciePrzedmiotu(() -> active);   // ten jeden event zwraca inny typ na starszych wydaniach
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
            ResourceKey<Level> dimension = player.level().dimension();
            Anchor anchor = ANCHORS.computeIfAbsent(player.getUUID(),
                    uuid -> new Anchor(dimension, player.position()));

            // Portal dziala i na pauzie (gracz tickuje normalnie). Ciagniecie go wtedy
            // na stare wspolrzedne wsadziloby go w skale albo nad pustka po drugiej
            // stronie - wiec zmiane wymiaru przyjmujemy i kotwiczymy od nowa.
            if (!anchor.dimension().equals(dimension)) {
                anchor = new Anchor(dimension, player.position());
                ANCHORS.put(player.getUUID(), anchor);
            }

            if (player.distanceToSqr(anchor.pos()) > SLACK_SQR) {
                player.connection.teleport(anchor.pos().x, anchor.pos().y, anchor.pos().z,
                        player.getYRot(), player.getXRot());
            }

            // Powietrze schodzi przez cala pauze, a obrazenia od utoniecia sa tylko
            // wstrzymane - bez tego gracz zamrozony pod woda dostawalby cala serie
            // w pierwszej sekundzie po odmrozeniu.
            player.setAirSupply(player.getMaxAirSupply());

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
                Compat.spowolnij(player);
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
            Compat.odspowolnij(player);
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
