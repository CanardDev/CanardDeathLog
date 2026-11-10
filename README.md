# CanardDeathLog

Keeps the history of every player's deaths: **what killed them, where, and when**.

Perfect for staff when a player says "I died for no reason, give me my stuff back": you can check in a few seconds what really happened.

## Features

- Every death is recorded with:
  - the cause (fall, lava, explosion, `/kill`...)
  - the killer: player, mob, or named mob (`Bob (Zombie)`)
  - shots are traced back to the shooter (skeleton, player with a bow...)
  - the world and coordinates
  - the date, in your time zone
- `/deaths <player>` with pages, newest first
- Works for offline players
- Keeps the last 100 deaths per player (configurable), the total keeps counting
- Optional `deaths.log` file that keeps everything forever
- Causes and mob names translated (English and French), fully editable
- Files are written in the background: no lag

## Commands

| Command | Description |
|---|---|
| `/deaths <player> [page]` | Show a player's deaths |
| `/deaths clear <player>` | Delete a player's deaths |
| `/deaths reload` | Reload the config and messages |

Aliases: `/deathlog`, `/morts`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `deathlog.view` | op | Use `/deaths <player>` |
| `deathlog.clear` | op | Use `/deaths clear` and `/deaths reload` |

## Configuration

```yaml
language: en            # en or fr
max-deaths-per-player: 100
per-page: 10
log-file: true
timezone: "Europe/Paris"
date-format: "dd/MM/yyyy HH:mm:ss"
```

Death causes and mob names can be renamed in `messages_en.yml` / `messages_fr.yml`.

## Requirements

- Paper 1.21 or newer (or a Paper fork like Purpur)
- Java 21

## Installation

1. Download the `.jar` from the [Releases](../../releases) page
2. Put it in your `plugins/` folder
3. Restart the server and edit `plugins/CanardDeathLog/config.yml`

## Building

```
mvn package
```

---

Made by **CanardDev**. Questions or custom plugins: [Discord](https://discord.com/users/982732557365964881)
