package dev.kajor.bossrun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Kasowanie folderu swiata - wydzielone z {@link BossRun}, bo to jedyny kawalek moda,
 * ktory usuwa dane, i jako jedyny da sie sprawdzic bez uruchamiania serwera.
 * Sprawdza go {@link SelfTest}.
 */
public final class WorldWipe {

    /** Pasy bezpieczenstwa: kasujemy wylacznie folder swiata tego serwera i nic ponadto. */
    public static boolean safe(Path serverDir, Path world) {
        Path dir = serverDir.toAbsolutePath().normalize();
        Path target = world.toAbsolutePath().normalize();

        if (!Files.isDirectory(target)) {
            BossRun.LOG.error("Folder swiata {} nie istnieje - nic nie kasuje", target);
            return false;
        }
        if (!Files.exists(target.resolve("level.dat"))) {
            BossRun.LOG.error("W {} nie ma level.dat - to nie wyglada na swiat, nic nie kasuje", target);
            return false;
        }
        if (target.equals(dir) || !target.startsWith(dir)) {
            BossRun.LOG.error("Folder swiata {} lezy poza katalogiem serwera {} - nic nie kasuje",
                    target, dir);
            return false;
        }
        return true;
    }

    /** @return ile plikow i katalogow usunieto. */
    public static int delete(Path world) throws IOException {
        try (var sciezki = Files.walk(world)) {
            return sciezki.sorted(Comparator.reverseOrder())
                    .mapToInt(p -> p.toFile().delete() ? 1 : 0)
                    .sum();
        }
    }

    private WorldWipe() {
    }
}
