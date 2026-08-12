<div align="center">

<sub><b>Polski</b> · <a href="CHANGELOG.en.md">English</a></sub>

</div>

# Historia zmian

Format wg [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/),
wersjonowanie wg [SemVer](https://semver.org/lang/pl/).

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
