package pl.skynetgames.bossrun;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hardcore z czterema bossami: Ender Dragon, Wither, Elder Guardian, Warden.
 * Ktokolwiek z druzyny zginie - swiat leci do kosza i zaczynacie od nowa.
 *
 * Zegar i cala gra chodza tylko wtedy, gdy komplet druzyny jest online. Gdy
 * ktos wyjdzie, swiat zamarza (wanilkowy /tick freeze) i czas staje - nikt nie
 * nadrabia postepu, kiedy reszta spi.
 *
 * Sam reset swiata robi skrypt na hoscie: mod zostawia plik RESET_WORLD w
 * katalogu serwera i sie wylacza. Proces, ktory ma skasowac folder swiata, nie
 * moze dzialac w srodku tego samego procesu, ktory ten swiat trzyma otwarty.
 */
public class BossRun implements ModInitializer {

    public static final String MOD_ID = "bossrun";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** Plik-sygnal dla bossrun_reset.py na hoscie. */
    public static final String RESET_FLAG = "RESET_WORLD";

    private static final int RESET_COUNTDOWN_TICKS = 100;
    private static final int TAB_REFRESH_TICKS = 20;
    /** Czas zapisujemy co 10 s - po awarii traci sie najwyzej tyle, a dysk nie pracuje co tick. */
    private static final int SAVE_EVERY_TICKS = 200;

    private static int resetCountdown = -1;
    private static String victim = "";
    private static int tabCounter = 0;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(BossRun::onServerStarted);
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
        // Stan lezy w config/, nie w swiecie - swiat znika przy kazdym resecie.
        State.load(server.getServerDirectory().resolve("config").resolve("bossrun.json"));
        Hud.setup(server);
        Hud.syncDeaths(server);
        resetCountdown = -1;

        State s = State.get();
        if (!server.isHardcore()) {
            LOG.warn("Serwer NIE jest w trybie hardcore - ustaw hardcore=true w server.properties");
        }
        LOG.info("Podejscie #{}, bossowie {}/{}, czas {}",
                s.attempt, s.bosses.size(), State.BOSS_IDS.size(), Hud.formatTime(s.ticks));
    }

    private static void tick(MinecraftServer server) {
        if (resetCountdown >= 0) {
            tickResetCountdown(server);
            return;
        }

        State s = State.get();
        List<String> missing = missingPlayers(server);
        boolean shouldRun = s.running && !s.allBossesDown() && missing.isEmpty();

        // setFrozen rozsyla pakiety do klientow, wiec wolamy je tylko przy zmianie stanu.
        if (server.tickRateManager().isFrozen() == shouldRun) {
            server.tickRateManager().setFrozen(!shouldRun);
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
        } else {
            // Zamrozony swiat zatrzymuje moby i czas, ale nie gracza - o to dba Freeze.
            Freeze.hold(server, secondTick);
            if (secondTick) Hud.actionBarAll(server, pauseReason(s, missing));
        }

        if (secondTick) Hud.pushTabList(server, status(server));
    }

    /** Krotkie zdanie na pasku akcji: dlaczego stoimy. */
    private static Component pauseReason(State s, List<String> missing) {
        if (s.allBossesDown()) {
            return Component.literal("✦ Wyzwanie ukonczone").withStyle(ChatFormatting.GOLD);
        }
        if (!s.running) {
            return Component.literal("⏸ Wpisz /start, gdy caly sklad bedzie na serwerze")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal("⏸ Czekamy na: " + String.join(", ", missing))
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
        if (s.allBossesDown()) {
            return Component.literal("✦ WYZWANIE UKONCZONE ✦")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }
        if (!s.running) {
            return Component.literal("⏸ czekam na /start").withStyle(ChatFormatting.YELLOW);
        }
        List<String> missing = missingPlayers(server);
        if (!missing.isEmpty()) {
            return Component.literal("⏸ ZAMROZONE — brakuje: " + String.join(", ", missing))
                    .withStyle(ChatFormatting.RED);
        }
        return Component.literal("▶ gra trwa").withStyle(ChatFormatting.GREEN);
    }

    private static void greet(ServerPlayer player) {
        State s = State.get();
        player.sendSystemMessage(Component.literal("☠ Boss Run Hardcore ")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("· podejscie #" + s.attempt + " · twoje zgony: "
                                + s.deathsOf(player.getUUID().toString()))
                        .withStyle(ChatFormatting.GRAY)));
        if (!s.running) {
            player.sendSystemMessage(Component.literal("Wpisz /start, gdy caly sklad bedzie na serwerze.")
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

            s.addDeath(player.getUUID().toString(), player.getGameProfile().name());
            Hud.syncDeaths(server);
            beginReset(server, player.getGameProfile().name());
            return;
        }

        if (!s.running || s.allBossesDown()) return;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (!s.addBoss(id)) return;

        int done = s.bosses.size();
        Hud.titleAll(server,
                Component.literal("⚔ BOSS POKONANY").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(done + "/" + State.BOSS_IDS.size() + " · " + Hud.formatTime(s.ticks))
                        .withStyle(ChatFormatting.YELLOW),
                5, 50, 15);
        playForAll(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);

        if (s.allBossesDown()) victory(server);
    }

    private static void victory(MinecraftServer server) {
        State s = State.get();
        s.running = false;
        if (s.bestTicks == 0L || s.ticks < s.bestTicks) s.bestTicks = s.ticks;
        s.save();
        server.tickRateManager().setFrozen(true);

        Hud.titleAll(server,
                Component.literal("✦ WYZWANIE UKONCZONE ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("czas: " + Hud.formatTime(s.ticks) + " · podejscie #" + s.attempt)
                        .withStyle(ChatFormatting.WHITE),
                10, 140, 20);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("✦ Cztery bossy padly w czasie " + Hud.formatTime(s.ticks)
                                + " (podejscie #" + s.attempt + "). Gratulacje!")
                        .withStyle(ChatFormatting.GOLD), false);
        playForAll(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void beginReset(MinecraftServer server, String who) {
        victim = who;
        resetCountdown = RESET_COUNTDOWN_TICKS;
        // Podczas odliczania nikt juz nic nie zmieni - zamrazamy, zeby nie zginal nikt drugi.
        server.tickRateManager().setFrozen(true);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("☠ " + who + " zginal. Swiat leci do kosza.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        playForAll(server, SoundEvents.WITHER_SPAWN);
    }

    private static void tickResetCountdown(MinecraftServer server) {
        Freeze.hold(server, resetCountdown % 20 == 0);
        if (resetCountdown % 20 == 0) {
            int seconds = resetCountdown / 20;
            Hud.titleAll(server,
                    Component.literal("☠ " + victim + " ZGINAL")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Component.literal(seconds > 0 ? "reset swiata za " + seconds + "..." : "resetuje swiat...")
                            .withStyle(ChatFormatting.RED),
                    0, 30, 5);
            if (seconds > 0) playForAll(server, SoundEvents.NOTE_BLOCK_BASEDRUM.value());
        }
        resetCountdown--;
        if (resetCountdown >= 0) return;

        doReset(server);
    }

    /** Zostawia sygnal dla hosta i gasi serwer - reszte robi bossrun_reset.py. */
    private static void doReset(MinecraftServer server) {
        State s = State.get();
        s.nextAttempt();
        try {
            Files.writeString(server.getServerDirectory().resolve(RESET_FLAG),
                    "attempt=" + s.attempt + "\nvictim=" + victim + "\n");
        } catch (IOException e) {
            // Bez pliku host nie skasuje swiata - lepiej nie gasic serwera, niz wstac w starym swiecie.
            LOG.error("Nie udalo sie zapisac flagi resetu - swiat NIE zostanie skasowany", e);
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("✕ Reset swiata nie wystartowal - sprawdz logi serwera.")
                            .withStyle(ChatFormatting.RED), false);
            return;
        }
        LOG.info("Reset swiata: podejscie #{} (zginal {})", s.attempt, victim);
        server.halt(false);
    }

    private static void playForAll(MinecraftServer server, SoundEvent sound) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }
}
