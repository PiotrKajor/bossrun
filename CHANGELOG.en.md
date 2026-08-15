<div align="center">

<sub><a href="CHANGELOG.md">Polski</a> · <b>English</b></sub>

</div>

# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versioning follows [SemVer](https://semver.org/).

## [1.4.0] — 2026-08-15

### Added

- **Minecraft 1.20.4 through 26.2.** The mod used to run on 26.2 only. It now ships as four
  jars — one per API era: `1.20.4–1.21.4`, `1.21.5–1.21.10`, `1.21.11` and `26.2`. The
  differences (effects via `Holder`, server directory as `File`/`Path`, `ResourceLocation`
  → `Identifier`, named permissions, `UseItemCallback` returning a different type) live in
  `src/compat/<era>/Compat.java`; the rest of the mod is shared.
- **`tools/build_all.py`** — builds every era with one command; the 1.20–1.21.x eras live on
  branch `mc21` (classic Loom with mappings), 26.x on `master`.

### Fixed

- **The loader requirement and the mixin compatibility level now follow the era.** Hardcoded
  `fabricloader >=0.19.3` and `JAVA_25` killed the mod at startup on 1.20.x before anything
  loaded — one as a missing dependency candidate, the other as an unrecognised level.

### Note

- **It will not go below 1.20.4.** The pause rests on vanilla `/tick freeze`
  (`TickRateManager`), which older releases do not have.

## [1.3.1] — 2026-08-15

### Changed

- **The filename carries the loader and the game version:** `bossrun-fabric-26.2-1.3.1.jar`
  instead of `bossrun-1.3.0.jar`. The mod is tied to one Minecraft release and the mod
  version alone never said which.

## [1.3.0] — 2026-08-13

### Added

- **Custom challenge goals** in `config/bossrun-config.json`. Four types: `KILL` (kill N entities),
  `HAVE` (hold an item), `REACH` (enter a dimension) and `ADVANCEMENT` (unlock an advancement,
  including your own from a datapack). That last type is the escape hatch for anything the other
  three cannot express, with no code added to the mod. The default set is the same four bosses as
  before, so nothing changes unless you open the config.
- Progress on counted goals is shown on the TAB list and in `/bossrun`.
- **The mod deletes the world folder itself**, after the server has shut down and released it.
  This used to need an external script on the host. Any auto-restart is enough; if you prefer your
  own script, set `deleteWorldOnDeath: false` and keep getting the `RESET_WORLD` file as before.
- `resetCountdownSeconds` and `freezeWhenIncomplete` in the config — both used to be hardcoded.

### Changed

- **Every message is English now**, overridable in `config/bossrun-messages.json`. The mod is
  server-side, so a client cannot translate anything on its own — the translation has to come from
  the server.
- `selfTest` also covers goal completion, loading an older state file, and the world-deletion
  safety belts (refusing the server directory, a directory above it, and any folder without
  `level.dat`).

Older state files load without migration — the `bosses` key kept its name.

## [1.2.2] — 2026-08-11

### Fixed

- The pause now holds player damage and survives a portal transition.

## [1.2.1] — 2026-08-11

### Fixed

- Being paused mid-air no longer builds up fall damage.

## [1.2.0] — 2026-08-11

### Changed

- The pause also blocks the inventory, crafting and dropping items — you used to be able to sort
  your backpack while the rest of the team was waiting.

## [1.1.0] — 2026-08-11

### Added

- The pause holds players too (vanilla `/tick freeze` stops the world but not player movement).
- State published for the website: whether the run is paused and who it is waiting for.

## [1.0.0] — 2026-08-11

First version: four bosses on hardcore, world reset on death, a death counter on TAB and a clock
that only runs with the full team online.
