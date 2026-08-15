<div align="center">

<sub><b>Polski</b> · <a href="CHANGELOG.en.md">English</a></sub>

</div>

# Historia zmian

Format wg [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/),
wersjonowanie wg [SemVer](https://semver.org/lang/pl/).

## [1.4.0] — 2026-08-15

### Dodane

- **Minecraft od 1.20.4 do 26.2.** Wcześniej mod chodził wyłącznie na 26.2. Teraz wychodzi
  w czterech jarach — po jednym na erę API: `1.20.4–1.21.4`, `1.21.5–1.21.10`, `1.21.11`
  i `26.2`. Różnice między erami (efekty przez `Holder`, katalog serwera jako `File`/`Path`,
  `ResourceLocation` → `Identifier`, nazwane uprawnienia, `UseItemCallback` z innym typem
  zwrotnym) siedzą w `src/compat/<era>/Compat.java`; reszta moda jest wspólna.
- **`tools/build_all.py`** — buduje wszystkie ery jednym poleceniem; ery 1.20–1.21.x żyją
  na gałęzi `mc21` (klasyczny Loom z mapowaniami), 26.x na `master`.

### Naprawione

- **Wymóg loadera i poziom zgodności mixina jadą teraz z erą.** Zaszyte na sztywno
  `fabricloader >=0.19.3` i `JAVA_25` wywalały moda przy starcie na 1.20.x, zanim
  cokolwiek zdążyło się wczytać — jedno przez brak kandydata na zależność, drugie przez
  nieznany poziom zgodności.

### Uwaga

- **Poniżej 1.20.4 mod nie zejdzie.** Pauza stoi na wanilkowym `/tick freeze`
  (`TickRateManager`), którego starsze wydania nie mają — bez tego nie ma czego zamrażać.

## [1.3.1] — 2026-08-15

### Zmienione

- **Nazwa pliku niesie silnik i wersję gry:** `bossrun-fabric-26.2-1.3.1.jar` zamiast
  `bossrun-1.3.0.jar`. Mod jest przywiązany do konkretnego wydania Minecrafta, a sama
  wersja moda tego nie mówiła.

## [1.3.0] — 2026-08-13

### Dodane

- **Własne cele wyzwania** w `config/bossrun-config.json`. Cztery typy: `KILL` (zabić N sztuk
  encji), `HAVE` (mieć przedmiot), `REACH` (wejść do wymiaru) i `ADVANCEMENT` (odblokować
  osiągnięcie — także własne z datapacka). Ostatni typ jest furtką na wszystko, czego nie da się
  wyrazić trzema pozostałymi, bez dopisywania kodu do moda. Domyślna zawartość to dotychczasowe
  cztery bossy, więc bez zaglądania do configu nic się nie zmienia.
- Postęp celów liczonych na sztuki widać na TAB-ie i przy `/bossrun`.
- **Mod sam kasuje folder świata** — po zamknięciu serwera, kiedy świat jest już zwolniony.
  Wcześniej wymagało to zewnętrznego skryptu na hoście. Wystarczy auto-restart serwera; kto woli
  własny skrypt, ustawia `deleteWorldOnDeath: false` i dostaje plik `RESET_WORLD` jak dotąd.
- `resetCountdownSeconds` i `freezeWhenIncomplete` w configu — jedno i drugie było wpisane na sztywno.

### Zmienione

- **Wszystkie teksty po angielsku**, z nadpisywaniem w `config/bossrun-messages.json`. Mod jest
  serwerowy, więc klient nie ma jak przetłumaczyć niczego sam — tłumaczenie musi wyjść z serwera.
- `selfTest` sprawdza dodatkowo odhaczanie celów, wczytanie starego pliku stanu i pasy
  bezpieczeństwa kasowania świata (odmowa dla katalogu serwera, dla katalogu wyżej i dla folderu
  bez `level.dat`).

Stary plik stanu wczytuje się bez migracji — klucz `bosses` został przy swojej nazwie.

## [1.2.2] — 2026-08-11

### Naprawione

- Pauza wstrzymuje obrażenia graczy i przeżywa przejście portalem.

## [1.2.1] — 2026-08-11

### Naprawione

- Pauza w powietrzu nie nabija już obrażeń od upadku.

## [1.2.0] — 2026-08-11

### Zmienione

- Pauza blokuje także ekwipunek, crafting i wyrzucanie przedmiotów — wcześniej dało się
  robić porządki w plecaku, gdy reszta drużyny czekała.

## [1.1.0] — 2026-08-11

### Dodane

- Pauza zatrzymuje także graczy (wanilkowe `/tick freeze` zatrzymuje świat, ale nie ruch gracza).
- Stan wystawiany dla strony WWW: czy gra stoi i na kogo czeka.

## [1.0.0] — 2026-08-11

Pierwsza wersja: cztery bossy na hardcore, reset świata po śmierci, licznik zgonów na TAB-ie
i zegar chodzący tylko przy pełnym składzie.
