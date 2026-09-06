package dev.kajor.bossrun;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hardcore z celami do odhaczenia - domyslnie czterema bossami. Ktokolwiek z druzyny
 * zginie, swiat leci do kosza i zaczynacie od nowa.
 *
 * Zegar i cala gra chodza tylko wtedy, gdy komplet druzyny jest online. Gdy ktos wyjdzie,
 * swiat zamarza (wanilkowy /tick freeze) i czas staje - nikt nie nadrabia postepu, kiedy
 * reszta spi.
 *
 * Swiat kasuje sam mod, ale dopiero po zamknieciu serwera: proces, ktory trzyma swiat
 * otwarty, nie moze usunac jego folderu. Po wyjsciu procesu wstaje panel albo systemd
 * i generuje swiat od nowa. Kto woli zalatwic to skryptem na hoscie, ustawia
 * deleteWorldOnDeath = false - plik-sygnal RESET_WORLD zostaje wypisany tak czy inaczej.
 */
public class BossRun implements ModInitializer {

    public static final String MOD_ID = "bossrun";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** Plik-sygnal dla zewnetrznego skryptu resetujacego, jesli ktos takiego uzywa. */
    public static final String RESET_FLAG = "RESET_WORLD";

    private static final int TAB_REFRESH_TICKS = 20;
    /** Czas zapisujemy co 10 s - po awarii traci sie najwyzej tyle, a dysk nie pracuje co tick. */
    private static final int SAVE_EVERY_TICKS = 200;

    private static int resetCountdown = -1;
    private static String victim = "";
    private static int tabCounter = 0;
    /** Ustawiane tuz przed halt() - kasowanie odbywa sie dopiero po zamknieciu swiata. */
    private static boolean deleteWorldOnStop = false;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(BossRun::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(BossRun::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(BossRun::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(BossRun::onDeath);
        Freeze.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> Cmd.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Hud.syncDeaths(server);
            greet(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> Freeze.forget(handler.getPlayer().getUUID()));

        LOG.info("Boss Run Hardcore gotowy");
    }

    private static void onServerStarted(MinecraftServer server) {
        // Config i stan lezą w config/, nie w swiecie - swiat znika przy kazdym resecie.
        Path configDir = Compat.katalogSerwera(server).resolve("config");
        Config.load(configDir.resolve("bossrun-config.json"));
        Msg.load(configDir.resolve("bossrun-messages.json"));
        State.load(configDir.resolve("bossrun.json"));
        Hud.setup(server);
        Hud.syncDeaths(server);
        resetCountdown = -1;
        deleteWorldOnStop = false;

        State s = State.get();
        if (!server.isHardcore()) {
            LOG.warn("Serwer NIE jest w trybie hardcore - ustaw hardcore=true w server.properties");
        }
        LOG.info("Podejscie #{}, cele {}/{}, czas {}",
                s.attempt, s.done.size(), Config.get().goals.size(), Hud.formatTime(s.ticks));
    }

    private static void tick(MinecraftServer server) {
        if (resetCountdown >= 0) {
            tickResetCountdown(server);
            return;
        }

        State s = State.get();
        List<String> missing = missingPlayers(server);
        boolean komplet = missing.isEmpty() || !Config.get().freezeWhenIncomplete;
        boolean shouldRun = s.running && !s.allGoalsDone() && komplet;

        // setFrozen rozsyla pakiety do klientow, wiec wolamy je tylko przy zmianie stanu.
        if (Compat.czyZamrozone(server) == shouldRun) {
            Compat.zamrozenie(server, !shouldRun);
        }

        boolean secondTick = ++tabCounter >= TAB_REFRESH_TICKS;
        if (secondTick) tabCounter = 0;

        // Strona WWW czyta ten sam plik stanu. Zapisujemy go przy zmianie pauzy albo
        // skladu online - a nie co tick, bo to jedyne, co strone interesuje na biezaco.
        if (s.frozen == shouldRun || !s.missing.equals(missing)) {
            s.frozen = !shouldRun;
            s.missing = new ArrayList<>(missing);
            s.save();
        }

        if (shouldRun) {
            Freeze.release(server);
            s.ticks++;
            if (s.ticks % SAVE_EVERY_TICKS == 0) s.save();
            if (Goals.scan(server) && s.allGoalsDone()) victory(server);
        } else {
            // Zamrozony swiat zatrzymuje moby i czas, ale nie gracza - o to dba Freeze.
            Freeze.hold(server, secondTick);
            if (secondTick) Hud.actionBarAll(server, pauseReason(s, missing));
        }

        if (secondTick) Hud.pushTabList(server, status(server));
    }

    /** Krotkie zdanie na pasku akcji: dlaczego stoimy. */
    private static Component pauseReason(State s, List<String> missing) {
        if (s.allGoalsDone()) {
            return Component.literal(Msg.of("pause.complete")).withStyle(ChatFormatting.GOLD);
        }
        if (!s.running) {
            return Component.literal(Msg.of("pause.await_start")).withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal(Msg.of("pause.missing", String.join(", ", missing)))
                .withStyle(ChatFormatting.RED);
    }

    /** Nicki uczestnikow, ktorych brakuje na serwerze. Pusta lista = gra moze isc. */
    public static List<String> missingPlayers(MinecraftServer server) {
        State s = State.get();
        List<String> missing = new ArrayList<>();
        for (String uuid : s.roster) {
            if (server.getPlayerList().getPlayer(UUID.fromString(uuid)) == null) {
                missing.add(s.names.getOrDefault(uuid, uuid));
            }
        }
        return missing;
    }

    private static Component status(MinecraftServer server) {
        State s = State.get();
        if (s.allGoalsDone()) {
            return Component.literal(Msg.of("status.complete"))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }
        if (!s.running) {
            return Component.literal(Msg.of("status.await_start")).withStyle(ChatFormatting.YELLOW);
        }
        List<String> missing = missingPlayers(server);
        if (!missing.isEmpty() && Config.get().freezeWhenIncomplete) {
            return Component.literal(Msg.of("status.frozen", String.join(", ", missing)))
                    .withStyle(ChatFormatting.RED);
        }
        return Component.literal(Msg.of("status.running")).withStyle(ChatFormatting.GREEN);
    }

    private static void greet(ServerPlayer player) {
        State s = State.get();
        player.sendSystemMessage(Component.literal(Msg.of("greet.header"))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(Msg.of("greet.details", s.attempt,
                                s.deathsOf(player.getUUID().toString())))
                        .withStyle(ChatFormatting.GRAY)));
        if (!s.running) {
            player.sendSystemMessage(Component.literal(Msg.of("greet.hint"))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void onDeath(Entity entity, net.minecraft.world.damagesource.DamageSource source) {
        Level level = entity.level();
        MinecraftServer server = level.getServer();
        if (server == null) return;
        State s = State.get();

        if (entity instanceof ServerPlayer player) {
            if (!s.running || resetCountdown >= 0) return;
            // Widz spoza skladu moze ginac do woli - swiata to nie dotyczy.
            if (!s.roster.contains(player.getUUID().toString())) return;

            s.addDeath(player.getUUID().toString(), Compat.nick(player));
            Hud.syncDeaths(server);
            beginReset(server, Compat.nick(player));
            return;
        }

        if (!s.running || s.allGoalsDone()) return;
        String id = Compat.idBytu(entity);
        if (Goals.onKill(server, id) && s.allGoalsDone()) victory(server);
    }

    private static void victory(MinecraftServer server) {
        State s = State.get();
        s.running = false;
        if (s.bestTicks == 0L || s.ticks < s.bestTicks) s.bestTicks = s.ticks;
        s.save();
        Compat.zamrozenie(server, true);

        Hud.titleAll(server,
                Component.literal(Msg.of("victory.title"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(Msg.of("victory.subtitle", Hud.formatTime(s.ticks), s.attempt))
                        .withStyle(ChatFormatting.WHITE),
                10, 140, 20);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(Msg.of("victory.chat", Hud.formatTime(s.ticks), s.attempt))
                        .withStyle(ChatFormatting.GOLD), false);
        playForAll(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void beginReset(MinecraftServer server, String who) {
        victim = who;
        resetCountdown = Config.get().resetCountdownTicks();
        // Podczas odliczania nikt juz nic nie zmieni - zamrazamy, zeby nie zginal nikt drugi.
        Compat.zamrozenie(server, true);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(Msg.of("death.chat", who))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        playForAll(server, SoundEvents.WITHER_SPAWN);
    }

    private static void tickResetCountdown(MinecraftServer server) {
        Freeze.hold(server, resetCountdown % 20 == 0);
        if (resetCountdown % 20 == 0) {
            int seconds = resetCountdown / 20;
            Hud.titleAll(server,
                    Component.literal(Msg.of("death.title", victim))
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Component.literal(seconds > 0
                                    ? Msg.of("death.countdown", seconds)
                                    : Msg.of("death.resetting"))
                            .withStyle(ChatFormatting.RED),
                    0, 30, 5);
            if (seconds > 0) playForAll(server, SoundEvents.NOTE_BLOCK_BASEDRUM.value());
        }
        resetCountdown--;
        if (resetCountdown >= 0) return;

        doReset(server);
    }

    /** Zostawia sygnal, zaznacza swiat do skasowania i gasi serwer. */
    private static void doReset(MinecraftServer server) {
        State s = State.get();
        s.nextAttempt();
        try {
            Files.writeString(Compat.katalogSerwera(server).resolve(RESET_FLAG),
                    "attempt=" + s.attempt + "\nvictim=" + victim + "\n");
        } catch (IOException e) {
            // Gdy swiatem zajmuje sie skrypt na hoscie, brak pliku znaczy brak resetu -
            // lepiej nie gasic serwera, niz wstac w starym swiecie.
            if (!Config.get().deleteWorldOnDeath) {
                LOG.error("Nie udalo sie zapisac flagi resetu - swiat NIE zostanie skasowany", e);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal(Msg.of("death.reset_failed")).withStyle(ChatFormatting.RED), false);
                return;
            }
            LOG.warn("Nie udalo sie zapisac flagi resetu - kasuje swiat sam", e);
        }
        deleteWorldOnStop = Config.get().deleteWorldOnDeath;
        LOG.info("Reset swiata: podejscie #{} (zginal {})", s.attempt, victim);
        server.halt(false);
    }

    /**
     * Tu swiat jest juz zamkniety, a proces jeszcze zyje - jedyny moment, w ktorym mod
     * moze skasowac wlasny folder swiata.
     */
    private static void onServerStopped(MinecraftServer server) {
        if (!deleteWorldOnStop) return;
        deleteWorldOnStop = false;

        Path world = server.getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
        if (!WorldWipe.safe(Compat.katalogSerwera(server), world)) return;
        try {
            int skasowane = WorldWipe.delete(world);
            LOG.info("Swiat skasowany: {} ({} plikow). Nowy powstanie przy nastepnym starcie.",
                    world.getFileName(), skasowane);
        } catch (IOException e) {
            LOG.error("Nie udalo sie skasowac swiata {} - zrob to recznie przed startem", world, e);
        }
    }

    public static void playForAll(MinecraftServer server, SoundEvent sound) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }
}
