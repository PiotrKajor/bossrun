package dev.kajor.bossrun;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * `./gradlew selfTest` - sprawdza to, co da sie policzyc bez serwera: licznik zgonow,
 * odhaczanie celow (takze liczonych na sztuki), roznice miedzy resetem swiata (zgony
 * zostaja) a /reset (zgony znikaja), przezycie restartu procesu oraz to, ze plik stanu
 * zapisany starsza wersja moda dalej sie wczytuje. Wyjatek = test nie przeszedl.
 */
public final class SelfTest {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("bossrun-test");
        Path file = dir.resolve("bossrun.json");
        Config.load(dir.resolve("bossrun-config.json"));
        State.load(file);
        State s = State.get();
        check(s.attempt == 1 && !s.running && s.ticks == 0L, "swiezy stan zaczyna od zera");

        s.addDeath("u1", "Kai");
        s.addDeath("u1", "Kai");
        s.addDeath("u2", "Speed");
        check(s.deathsOf("u1") == 2, "dwa zgony Kaia");
        check(s.deathsOf("u2") == 1, "jeden zgon Speeda");
        check(s.deathsOf("nieznany") == 0, "gracz bez zgonow ma 0, nie null");

        check(Config.get().goals.size() == 4, "domyslne wyzwanie to cztery cele");
        s.addDone("minecraft:wither");
        check(s.isDone("minecraft:wither"), "Wither odhaczony");
        s.addDone("minecraft:wither");
        check(s.done.size() == 1, "drugi Wither w tym samym podejsciu nic nie zmienia");
        check(!s.allGoalsDone(), "jeden cel to nie komplet");

        s.ticks = 12_345L;
        s.running = true;
        s.nextAttempt();
        check(s.attempt == 2 && s.ticks == 0L && s.done.isEmpty(),
                "nowe podejscie: czas i cele od zera");
        check(s.deathsOf("u1") == 2 && s.running,
                "reset swiata NIE kasuje zgonow ani nie zatrzymuje wyzwania");

        // Restart serwera = ten sam plik wczytany od nowa.
        State.load(file);
        check(State.get().deathsOf("u1") == 2 && State.get().attempt == 2,
                "stan przezyl restart procesu");

        for (Goal goal : Config.get().goals) State.get().addDone(goal.target());
        check(State.get().allGoalsDone(), "wszystkie cele odhaczone to komplet");

        State.get().wipe();
        check(State.get().deathsOf("u1") == 0 && State.get().attempt == 1
                && !State.get().running && State.get().roster.isEmpty(), "/reset czysci wszystko");

        // Stan z czasow, gdy wyzwaniem byly wylacznie bossy - klucz "bosses" musi dalej dzialac.
        Path stary = dir.resolve("stary.json");
        Files.writeString(stary, "{\"attempt\":7,\"bosses\":[\"minecraft:warden\"],\"deaths\":{\"u9\":3}}");
        State.load(stary);
        check(State.get().attempt == 7 && State.get().isDone("minecraft:warden")
                && State.get().deathsOf("u9") == 3, "stary plik stanu wczytuje sie bez migracji");

        // Cele liczone na sztuki i puste pola w configu.
        State.load(dir.resolve("liczniki.json"));
        Goal dziesiec = new Goal(Goal.Type.KILL, "minecraft:blaze", 10, null);
        check(dziesiec.label().equals("Blaze x10"), "cel bez nazwy dostaje ja z id");
        check(State.get().addProgress(dziesiec.target()) == 1, "pierwsze zabicie to 1");
        check(State.get().addProgress(dziesiec.target()) == 2, "drugie zabicie to 2");
        check(!State.get().isDone(dziesiec.target()), "postep sam z siebie nie konczy celu");

        Goal polamany = new Goal(null, null, 0, "  ").repaired();
        check(polamany.type() == Goal.Type.KILL && polamany.amount() == 1
                && polamany.target() != null, "cel z pustymi polami dostaje sensowne wartosci");
        check(!polamany.label().isBlank(), "pusta nazwa nie zostawia pustej etykiety");

        check(Msg.of("status.running").startsWith("▶"), "domyslne teksty sa dostepne");
        check(Msg.of("nie.ma.takiego.klucza").equals("nie.ma.takiego.klucza"),
                "nieznany klucz zwraca sam siebie, nie null");
        check(Msg.of("greet.details", 3, 5).contains("#3"), "podstawianie argumentow dziala");

        check(Hud.formatTime(0L).equals("00:00"), "zero to 00:00");
        check(Hud.formatTime(20L * 61L).equals("01:01"), "61 s to 01:01");
        check(Hud.formatTime(20L * 3661L).equals("1:01:01"), "ponad godzina dostaje pole godzin");

        // Kasowanie swiata - jedyny kawalek moda, ktory usuwa dane.
        Path serverDir = Files.createDirectory(dir.resolve("serwer"));
        Path swiat = Files.createDirectories(serverDir.resolve("world").resolve("region"));
        Files.writeString(serverDir.resolve("world").resolve("level.dat"), "udawany");
        Files.writeString(swiat.resolve("r.0.0.mca"), "udawany chunk");
        check(WorldWipe.safe(serverDir, serverDir.resolve("world")), "prawdziwy swiat wolno skasowac");
        check(!WorldWipe.safe(serverDir, serverDir), "katalogu serwera NIE wolno skasowac");
        check(!WorldWipe.safe(serverDir, dir), "katalogu powyzej serwera NIE wolno skasowac");
        check(!WorldWipe.safe(serverDir, serverDir.resolve("nie-ma-mnie")), "nieistniejacego folderu nie ruszamy");

        Path bezDat = Files.createDirectory(serverDir.resolve("cos-innego"));
        Files.writeString(bezDat.resolve("wazne.txt"), "nie kasuj mnie");
        check(!WorldWipe.safe(serverDir, bezDat), "folder bez level.dat to nie swiat");

        check(WorldWipe.delete(serverDir.resolve("world")) == 4, "swiat znika razem z zawartoscia");
        check(!Files.exists(serverDir.resolve("world")), "po skasowaniu folderu swiata nie ma");
        check(Files.exists(bezDat.resolve("wazne.txt")), "reszta katalogu serwera zostaje nietknieta");

        System.out.println("SelfTest OK");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("NIE PRZESZLO: " + what);
    }

    private SelfTest() {
    }
}
