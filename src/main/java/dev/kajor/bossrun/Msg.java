package dev.kajor.bossrun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wszystkie teksty widoczne dla graczy, po angielsku, z mozliwoscia nadpisania.
 *
 * Mod jest server-side - klient nie ma jego plikow, wiec {@code Component.translatable}
 * doleciałoby do gracza jako goly klucz. Tlumaczenie musi wiec zrobic serwer: wlasciciel
 * wrzuca {@code config/bossrun-messages.json} z tymi kluczami, ktore chce zmienic,
 * reszta zostaje angielska.
 */
public final class Msg {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static Map<String, String> custom = Map.of();

    static {
        put("greet.header", "☠ Boss Run Hardcore ");
        put("greet.details", "· attempt #%s · your deaths: %s");
        put("greet.hint", "Type /start once the whole team is on the server.");

        put("pause.complete", "✦ Challenge complete");
        put("pause.await_start", "⏸ Type /start once the whole team is on the server");
        put("pause.missing", "⏸ Waiting for: %s");

        put("status.complete", "✦ CHALLENGE COMPLETE ✦");
        put("status.await_start", "⏸ waiting for /start");
        put("status.frozen", "⏸ FROZEN — missing: %s");
        put("status.running", "▶ run in progress");

        put("goal.done.title", "⚔ GOAL DONE");
        put("goal.done.subtitle", "%s/%s · %s");
        put("goal.progress", "%s — %s/%s");

        put("victory.title", "✦ CHALLENGE COMPLETE ✦");
        put("victory.subtitle", "time: %s · attempt #%s");
        put("victory.chat", "✦ Every goal done in %s (attempt #%s). Congratulations!");

        put("death.chat", "☠ %s died. The world goes in the bin.");
        put("death.title", "☠ %s DIED");
        put("death.countdown", "world reset in %s...");
        put("death.resetting", "resetting the world...");
        put("death.reset_failed", "✕ World reset did not start — check the server log.");

        put("start.already_complete", "The challenge is already done — use /reset first.");
        put("start.already_running", "The run is already going.");
        put("start.nobody", "Nobody to sign up — the server is empty.");
        put("start.title", "▶ START");
        put("start.subtitle", "attempt #%s · %s");
        put("start.chat", "▶ The run has started. Team: %s. If anyone leaves, the world and the clock stop.");

        put("reset.chat", "↺ Clock and deaths wiped. The world stays — /start begins a new attempt.");

        put("cmd.header", "☠ Boss Run — attempt #%s · time %s · goals %s/%s");
        put("cmd.no_roster", "No team yet — type /start.");
        put("cmd.player", "%s%s — deaths: %s");
        put("cmd.goal_line", "%s %s");

        put("tab.title", "☠ BOSS RUN HARDCORE ☠");
        put("tab.attempt", "attempt #%s  ");
        put("tab.record", "★ record: %s");
    }

    private static void put(String key, String text) {
        DEFAULTS.put(key, text);
    }

    public static void load(Path file) {
        custom = Map.of();
        if (!Files.exists(file)) {
            write(file);
            return;
        }
        try {
            Map<String, String> wczytane = new Gson().fromJson(Files.readString(file),
                    new TypeToken<Map<String, String>>() { }.getType());
            if (wczytane != null) custom = wczytane;
        } catch (IOException | RuntimeException e) {
            BossRun.LOG.error("Nie udalo sie wczytac {}, zostaje angielski", file.getFileName(), e);
        }
    }

    /** Pierwszy start zostawia komplet kluczy po angielsku - jest co kopiowac i tlumaczyc. */
    private static void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULTS));
        } catch (IOException e) {
            BossRun.LOG.error("Nie udalo sie zapisac {}", file.getFileName(), e);
        }
    }

    public static String of(String key, Object... args) {
        String wzor = custom.getOrDefault(key, DEFAULTS.getOrDefault(key, key));
        try {
            return args.length == 0 ? wzor : String.format(wzor, args);
        } catch (RuntimeException e) {
            // Zle %s w recznie zmienionym pliku nie moze wywalic serwera w srodku wyzwania.
            BossRun.LOG.error("Zly wzorzec dla klucza {}: {}", key, wzor);
            return DEFAULTS.getOrDefault(key, key);
        }
    }

    private Msg() {
    }
}
