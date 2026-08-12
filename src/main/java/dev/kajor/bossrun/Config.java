package dev.kajor.bossrun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ustawienia wyzwania - osobno od stanu, bo stan zmienia sie w kazdym podejsciu,
 * a config wlasciciel serwera pisze raz.
 *
 * Domyslne wartosci sa dokladnie tym, czym mod byl wczesniej na sztywno: cztery bossy,
 * pieciosekundowe odliczanie i zamrazanie przy niepelnym skladzie. Kto nie tknie pliku,
 * nie zauwazy, ze cokolwiek stalo sie konfigurowalne.
 */
public class Config {

    /** Co trzeba zrobic, zeby ukonczyc podejscie. */
    public List<Goal> goals = new ArrayList<>(Goal.defaults());

    /** Ile sekund odliczania miedzy smiercia a resetem swiata. */
    public int resetCountdownSeconds = 5;

    /**
     * Czy mod ma sam skasowac folder swiata przy resecie.
     * Kasujemy dopiero po zamknieciu serwera - proces, ktory trzyma swiat otwarty,
     * nie moze go usunac. Wstaw {@code false}, jesli swiatem zajmuje sie skrypt na hoscie.
     */
    public boolean deleteWorldOnDeath = true;

    /** Czy brak kogokolwiek ze skladu zatrzymuje swiat i zegar. */
    public boolean freezeWhenIncomplete = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public static Config get() {
        if (instance == null) instance = new Config();
        return instance;
    }

    public static void load(Path file) {
        instance = null;
        try {
            if (Files.exists(file)) instance = GSON.fromJson(Files.readString(file), Config.class);
        } catch (IOException | RuntimeException e) {
            BossRun.LOG.error("Nie udalo sie wczytac configu, biore domyslny", e);
        }
        if (instance == null) instance = new Config();
        instance.repair();
        instance.save(file);
    }

    /** Recznie zepsuty JSON nie moze wywrocic serwera ani zostawic wyzwania bez celow. */
    private void repair() {
        if (goals == null || goals.isEmpty()) {
            goals = new ArrayList<>(Goal.defaults());
        } else {
            List<Goal> fixed = new ArrayList<>(goals.size());
            for (Goal g : goals) if (g != null) fixed.add(g.repaired());
            goals = fixed.isEmpty() ? new ArrayList<>(Goal.defaults()) : fixed;
        }
        if (resetCountdownSeconds < 0) resetCountdownSeconds = 0;
    }

    private void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            BossRun.LOG.error("Nie udalo sie zapisac configu", e);
        }
    }

    public int resetCountdownTicks() {
        return resetCountdownSeconds * 20;
    }
}
