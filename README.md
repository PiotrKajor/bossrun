# Boss Run Hardcore

Mod serwerowy (Fabric, Minecraft 26.2) do wyzwania „cztery bossy na hardcore” –
tego samego, które robią Speed i Kai.

**Zasady:** pokonać Ender Dragona, Withera, Elder Guardiana i Wardena w jednym
świecie hardcore. Ktokolwiek z drużyny zginie – świat leci do kosza i zaczynacie
od nowa. Zgony liczą się przez wszystkie podejścia i widać je na TAB-ie.

Działa na `mc.skynetgames.org`.

## Co robi

| Rzecz | Jak |
|-------|-----|
| Śmierć uczestnika | wielki napis „☠ NICK ZGINĄŁ”, 5 s odliczania, potem reset świata |
| Reset świata | mod gasi serwer i zostawia plik `RESET_WORLD`; `bossrun_reset.py` na hoście kasuje świat i podnosi instancję (~15 s) |
| Zgony na TAB-ie | wanilkowy scoreboard w slocie `list` – liczba obok nicka, główkę TAB rysuje sam |
| Czas gry | leci tylko wtedy, gdy **cały skład** jest online; nagłówek TAB-a pokazuje go na żywo |
| Ktoś wyszedł | `/tick freeze` – świat i zegar stają, nikt nie nadrabia postępu w pojedynkę |
| Bossowie | zabicie ogłaszane napisem i odhaczane na TAB-ie; 4/4 kończy wyzwanie i zatrzymuje zegar |

Klient nie potrzebuje moda – wszystko leci wanilkowymi pakietami.

## Komendy

| Komenda | Kto | Działanie |
|---------|-----|-----------|
| `/start` | każdy | ustala skład z graczy obecnych **w tej chwili** i rusza zegar |
| `/bossrun` | każdy | status: czas, bossowie, zgony, kogo brakuje |
| `/reset` | OP | zeruje czas, zgony i postęp (świata **nie** kasuje) |

Po resecie świata wyzwanie leci dalej samo – skład i `running` przeżywają reset,
więc gracze wracają do świeżego świata bez wpisywania `/start`.

## Stan

`config/bossrun.json` w katalogu serwera – celowo **poza** folderem świata, bo
świat znika przy każdej śmierci. Trzyma zgony, skład, numer podejścia i rekord.

## Build

```bash
./gradlew build      # → build/libs/bossrun-<wersja>.jar
./gradlew selfTest   # sprawdza liczniki, persystencję i format czasu
```

Wymaga JDK 25 (MC 26.x). Jar wrzucić do `mods/` obok Fabric API dla 26.2.

## Druga połowa: reset świata

`bossrun_reset.py` (repo `skynet-server`) chodzi na hoście jako usługa
`skynet-bossrun-reset`. Proces, który trzyma świat otwarty, nie skasuje go sam –
dlatego mod tylko sygnalizuje, a kasuje ktoś z zewnątrz.

Skrypt kasuje świat dopiero, gdy **dwa niezależne źródła** potwierdzą, że serwer
zszedł (AMP i brak procesu w kontenerze), i tylko jeśli katalog wygląda na świat
Minecrafta i leży w katalogu instancji. `--selftest` sprawdza te odmowy.
