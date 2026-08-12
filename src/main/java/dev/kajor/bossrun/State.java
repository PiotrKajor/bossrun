package dev.kajor.bossrun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caly trwaly stan wyzwania w jednym pliku JSON - i celowo POZA folderem swiata,
 * bo swiat kasujemy przy kazdej smierci. Zgony, sklad druzyny i numer podejscia
 * musza to przezyc; czas i odhaczone cele nie - te zeruje sam mod przed resetem.
 *
 * Klasa nie dotyka zadnej klasy Minecrafta, dzieki czemu SelfTest sprawdza ja
 * bez uruchamiania serwera.
 */
public class State {

    public int attempt = 1;
    public boolean running = false;
    /** Czas biezacego podejscia w tickach - liczony tylko wtedy, gdy gra faktycznie chodzi. */
    public long ticks = 0L;
    /** Najlepszy ukonczony przebieg (0 = jeszcze zaden). */
    public long bestTicks = 0L;
    /** UUID uczestnikow ustalonych przy /start - tylko oni zamrazaja gre i resetuja swiat. */
    public List<String> roster = new ArrayList<>();
    public Map<String, String> names = new LinkedHashMap<>();
    public Map<String, Integer> deaths = new LinkedHashMap<>();

    /**
     * Odhaczone cele (po {@code target}). W JSON-ie klucz zostaje "bosses" z czasow, gdy
     * wyzwaniem byly wylacznie cztery bossy: stare pliki stanu wczytuja sie bez migracji,
     * a strona WWW czyta ten sam klucz.
     */
    @SerializedName("bosses")
    public List<String> done = new ArrayList<>();

    /** Postep celow liczonych na sztuki (KILL z amount > 1). */
    public Map<String, Integer> progress = new LinkedHashMap<>();

    // Ponizsze pola sa dla strony WWW, nie dla samej gry: mod jest jedynym miejscem,
    // ktore wie, czy gra stoi i na kogo czeka. Bez nich strona musialaby to zgadywac.
    /** Czy gra stoi (pauza albo brak startu). Strona po tym poznaje, czy tykac zegar. */
    public boolean frozen = true;
    /** Nicki uczestnikow, ktorych brakuje na serwerze. */
    public List<String> missing = new ArrayList<>();
    /** Kiedy ten plik ostatnio zapisano — strona dolicza czas, ktory uplynal od zapisu. */
    public long savedAtMs = 0L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static State instance;
    private static Path file;

    public static State get() {
        if (instance == null) instance = new State();
        return instance;
    }

    public static void load(Path f) {
        file = f;
        instance = null;
        try {
            if (Files.exists(f)) instance = GSON.fromJson(Files.readString(f), State.class);
        } catch (IOException | RuntimeException e) {
            BossRun.LOG.error("Nie udalo sie wczytac stanu, zaczynam od zera", e);
        }
        if (instance == null) instance = new State();
        instance.repair();
        instance.save();
    }

    /** Recznie zepsuty albo starszy JSON nie moze wywrocic serwera nullem. */
    private void repair() {
        if (roster == null) roster = new ArrayList<>();
        if (names == null) names = new LinkedHashMap<>();
        if (deaths == null) deaths = new LinkedHashMap<>();
        if (done == null) done = new ArrayList<>();
        if (progress == null) progress = new LinkedHashMap<>();
        if (missing == null) missing = new ArrayList<>();
        if (attempt < 1) attempt = 1;
    }

    public void save() {
        if (file == null) return;
        savedAtMs = System.currentTimeMillis();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            BossRun.LOG.error("Nie udalo sie zapisac stanu", e);
        }
    }

    public int deathsOf(String uuid) {
        return deaths.getOrDefault(uuid, 0);
    }

    public void addDeath(String uuid, String name) {
        deaths.merge(uuid, 1, Integer::sum);
        names.put(uuid, name);
        save();
    }

    public boolean isDone(String target) {
        return done.contains(target);
    }

    public void addDone(String target) {
        if (!done.contains(target)) {
            done.add(target);
            save();
        }
    }

    /** @return ile sztuk tego celu juz zaliczono. */
    public int addProgress(String target) {
        int n = progress.merge(target, 1, Integer::sum);
        save();
        return n;
    }

    public int progressOf(String target) {
        return progress.getOrDefault(target, 0);
    }

    public boolean allGoalsDone() {
        for (Goal goal : Config.get().goals) {
            if (!done.contains(goal.target())) return false;
        }
        return true;
    }

    /** Nowy swiat po smierci: czas i cele od zera, zgony i sklad zostaja. */
    public void nextAttempt() {
        attempt++;
        ticks = 0L;
        done.clear();
        progress.clear();
        save();
    }

    /** /reset - zeruje czas, zgony i postep. Sklad druzyny trzeba ustalic ponownie przez /start. */
    public void wipe() {
        attempt = 1;
        running = false;
        ticks = 0L;
        bestTicks = 0L;
        roster.clear();
        names.clear();
        deaths.clear();
        done.clear();
        progress.clear();
        save();
    }
}
