package pl.skynetgames.bossrun;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * `./gradlew selfTest` - sprawdza to, co da sie policzyc bez serwera: licznik zgonow,
 * postep bossow, roznice miedzy resetem swiata (zgony zostaja) a /reset (zgony znikaja)
 * oraz to, ze stan przezywa restart procesu. Wyjatek = test nie przeszedl.
 */
public final class SelfTest {

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempDirectory("bossrun-test").resolve("bossrun.json");
        State.load(file);
        State s = State.get();
        check(s.attempt == 1 && !s.running && s.ticks == 0L, "swiezy stan zaczyna od zera");

        s.addDeath("u1", "Kai");
        s.addDeath("u1", "Kai");
        s.addDeath("u2", "Speed");
        check(s.deathsOf("u1") == 2, "dwa zgony Kaia");
        check(s.deathsOf("u2") == 1, "jeden zgon Speeda");
        check(s.deathsOf("nieznany") == 0, "gracz bez zgonow ma 0, nie null");

        check(s.addBoss("minecraft:wither"), "pierwszy Wither sie liczy");
        check(!s.addBoss("minecraft:wither"), "drugi Wither w tym samym podejsciu juz nie");
        check(!s.addBoss("minecraft:zombie"), "zombie to nie boss");
        check(!s.allBossesDown(), "jeden boss to nie komplet");

        s.ticks = 12_345L;
        s.running = true;
        s.nextAttempt();
        check(s.attempt == 2 && s.ticks == 0L && s.bosses.isEmpty(), "nowe podejscie: czas i bossowie od zera");
        check(s.deathsOf("u1") == 2 && s.running, "reset swiata NIE kasuje zgonow ani nie zatrzymuje wyzwania");

        // Restart serwera = ten sam plik wczytany od nowa.
        State.load(file);
        check(State.get().deathsOf("u1") == 2 && State.get().attempt == 2, "stan przezyl restart procesu");

        for (String id : State.BOSS_IDS) State.get().addBoss(id);
        check(State.get().allBossesDown(), "cztery bossy to komplet");

        State.get().wipe();
        check(State.get().deathsOf("u1") == 0 && State.get().attempt == 1
                && !State.get().running && State.get().roster.isEmpty(), "/reset czysci wszystko");

        check(Hud.formatTime(0L).equals("00:00"), "zero to 00:00");
        check(Hud.formatTime(20L * 61L).equals("01:01"), "61 s to 01:01");
        check(Hud.formatTime(20L * 3661L).equals("1:01:01"), "ponad godzina dostaje pole godzin");

        System.out.println("SelfTest OK");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("NIE PRZESZLO: " + what);
    }

    private SelfTest() {
    }
}
