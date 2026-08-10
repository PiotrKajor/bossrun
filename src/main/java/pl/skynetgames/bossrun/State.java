package pl.skynetgames.bossrun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
 * musza to przezyc; czas i zabici bossowie nie - te zeruje sam mod przed resetem.
 *
 * Klasa nie dotyka zadnej klasy Minecrafta, dzieki czemu SelfTest sprawdza ja
 * bez uruchamiania serwera.
 */
public class State {

    /** Bossowie do pokonania - kolejnosc = kolejnosc wyswietlania na TAB-ie. */
    public static final List<String> BOSS_IDS = List.of(
            "minecraft:ender_dragon", "minecraft:wither",
            "minecraft:elder_guardian", "minecraft:warden");

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
    public List<String> bosses = new ArrayList<>();

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
        if (bosses == null) bosses = new ArrayList<>();
        if (attempt < 1) attempt = 1;
    }

    public void save() {
        if (file == null) return;
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

    /** @return true, jesli to pierwszy raz w tym podejsciu (czyli warto oglosic). */
    public boolean addBoss(String id) {
        if (!BOSS_IDS.contains(id) || bosses.contains(id)) return false;
        bosses.add(id);
        save();
        return true;
    }

    public boolean allBossesDown() {
        return bosses.size() >= BOSS_IDS.size();
    }

    /** Nowy swiat po smierci: czas i bossowie od zera, zgony i sklad zostaja. */
    public void nextAttempt() {
        attempt++;
        ticks = 0L;
        bosses.clear();
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
        bosses.clear();
        save();
    }
}
