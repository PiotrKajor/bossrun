#!/usr/bin/env python3
"""Wgrywa na Modrinth po jednej wersji na erę — jary z dist/, zakresy z build_all.py.

Jedno wydanie moda = kilka plików, a Modrinth trzyma listę wersji gry przy wersji, nie przy
pliku. Dlatego każda era idzie jako osobna wersja projektu: `1.4.0+1.20.4-1.21.4` z listą
1.20.4…1.21.4. Numer wersji z sufiksem ery, bo w obrębie projektu musi być unikalny.

Wymaga MODRINTH_TOKEN w /etc/skynet/secrets. Bez `--wgraj` tylko pokazuje, co poszłoby na
serwer — wgranie wersji jest nieodwracalne inaczej niż przez ręczne skasowanie.

Użycie:
    python3 tools/publish_modrinth.py            # podgląd
    python3 tools/publish_modrinth.py --wgraj
"""
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_all import ERY, REPO, DIST  # noqa: E402

PROJEKT = "pgcCo8DO"          # modrinth.com/mod/boss-run-hardcore
SEKRETY = Path("/etc/skynet/secrets")
ZALEZNOSCI = [{"project_id": "P7dR8mSH", "dependency_type": "required"}]   # Fabric API
# Ery wyróżnione na stronie projektu — Modrinth pozwala na pięć, ale wyróżnianie
# wszystkiego jest tym samym, co niewyróżnianie niczego.
WYROZNIONE = {"mc26", "mc2111"}


def sekret(klucz: str) -> str:
    for linia in SEKRETY.read_text().splitlines():
        k, _, v = linia.strip().partition("=")
        if k.strip() == klucz:
            return v.strip().strip('"')
    return ""


def wersja_moda() -> str:
    return (REPO / "gradle.properties").read_text().split("mod_version=")[1].split("\n")[0].strip()


def changelog(wersja: str) -> str:
    tekst = (REPO / "CHANGELOG.en.md").read_text()
    start = tekst.find(f"## [{wersja}]")
    if start < 0:
        return f"Release {wersja}."
    nastepna = tekst.find("\n## [", start + 1)
    return tekst[start:nastepna if nastepna > 0 else len(tekst)].strip()


def main() -> int:
    wgraj = "--wgraj" in sys.argv
    wersja = wersja_moda()
    token = sekret("MODRINTH_TOKEN")
    if wgraj and not token:
        sys.exit("BŁĄD: brak MODRINTH_TOKEN w /etc/skynet/secrets")
    opis = changelog(wersja)

    for wpis in ERY:
        jar = DIST / f"bossrun-fabric-{wpis['range']}-{wersja}.jar"
        if not jar.exists():
            sys.exit(f"BŁĄD: nie ma {jar} — najpierw tools/build_all.py")
        gry = wpis["gry"]
        dane = {
            "name": f"Boss Run Hardcore {wersja} — Minecraft {wpis['range'].replace('-', ' – ')}",
            "version_number": f"{wersja}+{wpis['range']}",
            "changelog": opis,
            "dependencies": ZALEZNOSCI,
            "game_versions": gry,
            "version_type": "release",
            "loaders": ["fabric"],
            "featured": wpis["era"] in WYROZNIONE,
            "project_id": PROJEKT,
            "file_parts": ["file"],
            "primary_file": "file",
        }
        print(f"{jar.name}: {dane['version_number']} → {gry[0]}…{gry[-1]}"
              + ("  ★" if dane["featured"] else ""))
        if not wgraj:
            continue

        plik_json = Path("/tmp") / f"mr-{wpis['era']}.json"
        plik_json.write_text(json.dumps(dane))
        r = subprocess.run(
            ["curl", "-s", "-X", "POST", "https://api.modrinth.com/v2/version",
             "-H", f"Authorization: {token}",
             "-F", f"data=@{plik_json};type=application/json",
             "-F", f"file=@{jar};type=application/java-archive"],
            capture_output=True, text=True)
        plik_json.unlink(missing_ok=True)
        odp = json.loads(r.stdout or "{}")
        if not odp.get("id"):
            print(f"  ODRZUCONE: {odp.get('description', r.stdout[:200])}")
            return 1
        # Mod gra po obu stronach (HUD u gracza) — pole jest dopiero w v3.
        subprocess.run(["curl", "-s", "-X", "PATCH",
                        f"https://api.modrinth.com/v3/version/{odp['id']}",
                        "-H", f"Authorization: {token}",
                        "-H", "Content-Type: application/json",
                        "-d", json.dumps({"environment": "client_and_server"})],
                       capture_output=True)
        print(f"  wgrane: {odp['id']}")

    if not wgraj:
        print("\n(podgląd — dodaj --wgraj, żeby faktycznie wysłać)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
