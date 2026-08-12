<div align="center">

<sub><a href="README.md">Polski</a> · <b>English</b></sub>

<sub><a href="CHANGELOG.en.md">Changelog</a></sub>

# ☠ Boss Run Hardcore

**A hardcore challenge for the whole team: anyone dies and the world goes in the bin.**

By default you have to beat the Ender Dragon, the Wither, an Elder Guardian and the Warden in a
single world — but the goals live in a config file, so the challenge can be anything. Deaths are
counted across every attempt and shown on the TAB list.

[![Download jar](https://img.shields.io/badge/Download-bossrun.jar-4fb4ff?style=for-the-badge)](../../releases/latest)
&nbsp;
![Minecraft](https://img.shields.io/badge/Minecraft%2026.2%20%7C%20Fabric-2a3245?style=for-the-badge)
&nbsp;
![License](https://img.shields.io/badge/license-MIT-3ddc84?style=for-the-badge)

</div>

---

**Server-side** — players need nothing installed, everything runs on vanilla packets.
Requires [Fabric API](https://modrinth.com/mod/fabric-api) and Java 25 (MC 26.2).

## What it does

| Thing | How |
|-------|-----|
| A participant dies | a huge "☠ NAME DIED" title, a countdown, then the world reset |
| World reset | the mod stops the server and **deletes the world folder itself**; your panel or systemd brings the server back and it generates a fresh world |
| Deaths on TAB | a vanilla scoreboard in the `list` slot — the number sits next to the name, TAB draws the head itself |
| Run timer | only ticks while the **whole team** is online; the TAB header shows it live |
| Someone left | `/tick freeze` — the world and the clock stop, nobody gets ahead alone |
| Goals | completing one is announced and ticked off on TAB; a full set ends the run and stops the clock |

## World reset — what you need

The mod deletes the world **after the server has shut down**: a process holding the world open
cannot remove its own folder. So you need **auto-restart** — a hosting panel, `Restart=always`
in systemd, or a plain loop in `run.sh`. On restart the server finds an empty directory and
generates the world from scratch.

Want the same seed every time? Set `level-seed` in `server.properties` and every attempt starts
from the same world. That needs nothing from the mod.

Prefer to handle the world yourself? Set `deleteWorldOnDeath: false`. The mod then only stops the
server and leaves a `RESET_WORLD` file in its directory (with the attempt number and the name of
whoever died) for your own script to react to.

## Goals

`config/bossrun-config.json`, written on first start:

```json
{
  "goals": [
    { "type": "KILL", "target": "minecraft:ender_dragon", "amount": 1, "name": "Ender Dragon" },
    { "type": "HAVE", "target": "minecraft:elytra", "amount": 1, "name": "Elytra" },
    { "type": "REACH", "target": "minecraft:the_end", "amount": 1, "name": "The End" },
    { "type": "ADVANCEMENT", "target": "minecraft:nether/all_potions", "amount": 1 }
  ],
  "resetCountdownSeconds": 5,
  "deleteWorldOnDeath": true,
  "freezeWhenIncomplete": true
}
```

| Type | Condition |
|------|-----------|
| `KILL` | kill `amount` entities with that id (progress shows on TAB) |
| `HAVE` | hold `amount` of that item in the inventory |
| `REACH` | enter the dimension with that id |
| `ADVANCEMENT` | unlock an advancement — vanilla or **your own from a datapack** |

`ADVANCEMENT` is the escape hatch for anything the other three types cannot express: write the
condition as a datapack advancement and put its id here. The whole team counts — an elytra on one
player completes the goal for everyone. `name` is optional; without it the label is derived from
the id.

| Key | Default | Meaning |
|-----|---------|---------|
| `resetCountdownSeconds` | `5` | seconds between the death and the reset |
| `deleteWorldOnDeath` | `true` | whether the mod deletes the world folder itself |
| `freezeWhenIncomplete` | `true` | whether a missing team member stops the world and the clock |

## Commands

| Command | Who | What it does |
|---------|-----|--------------|
| `/start` | anyone | locks the team to whoever is online **right now** and starts the clock |
| `/bossrun` | anyone | status: time, goals, deaths, who is missing |
| `/reset` | OP | wipes the clock, deaths and progress (does **not** delete the world) |

After a world reset the run continues on its own — the team and the running flag survive it, so
players come back to a fresh world without typing `/start` again.

## Messages

Everything players see is English and lives in `config/bossrun-messages.json` (written on first
start with the full set of keys). The mod is server-side, so translations cannot happen on the
client — you change the values in that file and that is it. Keys you leave alone stay English.

## State

`config/bossrun.json` — deliberately **outside** the world folder, because the world disappears on
every death. It holds deaths, the team, the attempt number and the record.

## Building

```bash
./gradlew build      # → build/libs/bossrun-<version>.jar
./gradlew selfTest   # counters, persistence, time format and the world-deletion safety belts
```

Requires JDK 25 (MC 26.x). Drop the jar into `mods/` next to Fabric API for 26.2.

## License

MIT — see [LICENSE](LICENSE).
