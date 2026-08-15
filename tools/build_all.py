#!/usr/bin/env python3
"""Buduje po jednym jarze na erę Minecrafta; wynik ląduje w dist/.

Jedna era = jeden zestaw API, czyli jeden jar. Granice er to miejsca, w których coś
w Minecrafcie pękło — nie kolejne wydania gry:

    1.20.4 – 1.21.4   MobEffects.MOVEMENT_SLOWDOWN, efekt bez Holdera, getServerDirectory()
                      zwraca File, UseItemCallback oddaje InteractionResultHolder
    1.21.5 – 1.21.10  efekty przez Holder, katalog serwera jako Path
    1.21.11           ResourceLocation przemianowany na Identifier, uprawnienia nazwane,
                      GameProfile jako rekord — czyli już to samo, co w 26.x
    26.x              Minecraft bez obfuskacji: inny build.gradle, gałąź master

Dolna granica to 1.20.4, bo mod stoi na `/tick freeze` (TickRateManager), którego
wcześniejsze wydania po prostu nie mają — zamrożenie czasu jest tu całą mechaniką,
nie ozdobą.

Różnice mieszkają w src/compat/<era>/Compat.java; ery 1.20–1.21.x są na gałęzi `mc21`
(klasyczny Loom z mapowaniami), 26.x na `master`.

Użycie:
    python3 tools/build_all.py            # wszystkie ery
    python3 tools/build_all.py mc20 mc26  # wybrane
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DIST = REPO / "dist"
# JDK dla samego Gradle — poziom bajtkodu ustawia -Pjava_version. 26.x wymaga 25.
JDK = {17: "/usr/lib/jvm/java-21-openjdk-amd64",
       21: "/usr/lib/jvm/java-21-openjdk-amd64",
       25: "/opt/jdk-25"}

ERY = [
    {"era": "mc20", "galaz": "mc21", "mc": "1.20.4", "fapi": "0.97.3+1.20.4", "java": 17,
     "range": "1.20.4-1.21.4", "depend": ">=1.20.4 <1.21.5", "loader": ">=0.15.0",
     "gry": ["1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4"]},
    {"era": "mc21", "galaz": "mc21", "mc": "1.21.8", "fapi": "0.136.1+1.21.8", "java": 21,
     "range": "1.21.5-1.21.10", "depend": ">=1.21.5 <1.21.11", "loader": ">=0.15.0",
     "gry": ["1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"]},
    {"era": "mc2111", "galaz": "mc21", "mc": "1.21.11", "fapi": "0.141.6+1.21.11", "java": 21,
     "range": "1.21.11", "depend": ">=1.21.11 <26", "loader": ">=0.16.0", "gry": ["1.21.11"]},
    {"era": "mc26", "mc": "26.2", "fapi": "0.157.0+26.2", "java": 25,
     "range": "26.2", "depend": "~26.2", "loader": ">=0.19.3", "gry": ["26.2"]},
]


def buduj(wpis: dict) -> Path:
    katalog = REPO
    if wpis.get("galaz"):
        katalog = REPO / "build" / f"worktree-{wpis['galaz']}"
        if not katalog.exists():
            subprocess.run(["git", "worktree", "add", "-f", str(katalog), wpis["galaz"]],
                           cwd=REPO, check=True, capture_output=True)
        # Wersja moda ma być jedna dla całego wydania, także na gałęzi z erami.
        wersja_master = (REPO / "gradle.properties").read_text().split("mod_version=")[1].split("\n")[0]
        props = katalog / "gradle.properties"
        tekst = props.read_text()
        stara = tekst.split("mod_version=")[1].split("\n")[0]
        if stara.strip() != wersja_master.strip():
            props.write_text(tekst.replace(f"mod_version={stara}", f"mod_version={wersja_master}"))

    subprocess.run(
        ["./gradlew", "build", "--no-daemon", "--no-configuration-cache", "-q",
         f"-Pcompat_era={wpis['era']}", f"-Pminecraft_version={wpis['mc']}",
         f"-Pfabric_version={wpis['fapi']}", f"-Pjava_version={wpis['java']}",
         f"-Pmc_range={wpis['range']}", f"-Pmc_depend={wpis['depend']}",
         f"-Ploader_depend={wpis['loader']}"],
        cwd=katalog, check=True, timeout=1800,
        env={**os.environ, "JAVA_HOME": JDK[wpis["java"]]})

    wersja = (REPO / "gradle.properties").read_text().split("mod_version=")[1].split("\n")[0].strip()
    nazwa = f"bossrun-fabric-{wpis['range']}-{wersja}.jar"
    jar = katalog / "build" / "libs" / nazwa
    if not jar.exists():
        raise SystemExit(f"BŁĄD: build nie zostawił {jar}")
    DIST.mkdir(exist_ok=True)
    cel = DIST / nazwa
    shutil.copy2(jar, cel)
    return cel


def main() -> int:
    wybrane = sys.argv[1:]
    for wpis in ERY:
        if wybrane and wpis["era"] not in wybrane:
            continue
        print(f"\n=== {wpis['era']}: {wpis['range']} (MC {wpis['mc']}, Java {wpis['java']})")
        print(f"  → {buduj(wpis).relative_to(REPO)}")
    print("\nGotowe.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
