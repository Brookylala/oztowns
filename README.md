# OzTowns

A Spigot/Paper towns plugin for **Minecraft 1.21.11** with chunk claims, town roles, bank/upkeep, spawn management, GUI menus, and optional PlaceholderAPI/DecentHolograms/Vault integration.

## Links
https://github.com/Brookylala/OminousChestLock - My version of cev-api OminousChestLock required for raiding

https://github.com/Brookylala/ozanarchy-economy - Economy Usage (Either this or Vault)

https://github.com/DecentSoftware-eu/DecentHolograms - For use with Town Holograms 


## Features

- Town lifecycle: create, rename, set mayor, leave, abandon
- Chunk claiming with adjacency checks and unclaim restrictions
- Role system: mayor/officer/member + member permission nodes
- Town bank with deposits, withdrawals, balance, upkeep billing
- Spawn system with delay, cooldown, and automatic reminders/deletion for towns without spawn
- Raiding system: lockpick enemy town Lodestone, survive raid timer, and take town rewards
- GUI support for main menu, bank, members, and member permissions
- Chunk visualizer particles for own/enemy/wilderness claims
- Optional town chat command (`/tm`)
- Optional PlaceholderAPI placeholders and DecentHolograms spawn holograms

## Raiding

- Raids are triggered by successfully lockpicking a **town spawn Lodestone**.
- Raiders must survive for **60 seconds** to complete the raid.
- Raid fails if the raider takes damage, dies, or disconnects.
- On success:
  - If the raider is in a town, raided bank funds transfer to the raider town bank.
  - If the raider is not in a town, funds are paid directly to the player.
  - The raided town is deleted (claims, members, bank, and town data).
- Players cannot raid their own town Lodestone.
- Raider and defenders get raid bossbars (`bossbar.raid.*` in `config.yml`).

## Requirements

- Java **21**
- Spigot/Paper API **1.21.11**
- Storage backend:
  - MySQL (when `storage.type: mysql`), or
  - SQLite (when `storage.type: sqlite`)
- Economy backend (pick one):
  - `ozanarchy-economy` plugin (when `economy-plugin: ozanarchy-economy`) (default), or
  - `Vault` plugin (when `economy-plugin: vault`) plus any Vault-compatible economy plugin
- Optional plugins:
  - `OminousChestLock` (enables raiding lockpick integration)
  - `PlaceholderAPI`
  - `DecentHolograms`

## Installation

1. Build the plugin jar (or use your release jar).
2. Put the jar in your server `plugins/` folder.
3. Ensure required dependencies are installed.
4. Start the server once to generate configs.
5. Edit `plugins/ozanarchy-towns/config.yml` with your MySQL credentials.
6. Restart server.

## Build (Maven)

```bash
mvn clean package
```

Outputs are created in `target/`.

## Commands

- `/towns` (`/town`, `/oztowns`, `/towny`)
- `/townbank` (`/tbank`, `/townsbank`)
- `/townadmin` (`/tadmin`)
- `/tm <message>`

Use `/towns help`, `/townbank help`, and `/townadmin help` in-game for detailed subcommands.

## Permissions

Main:

- `oztowns.commands`
- `oztowns.commands.bank`
- `oztowns.admin`
- `oztowns.admin.protectionbypass`

Town command nodes:

- `oztowns.commands.create`
- `oztowns.commands.rename`
- `oztowns.commands.setspawn`
- `oztowns.commands.spawn`
- `oztowns.commands.claim`
- `oztowns.commands.unclaim`
- `oztowns.commands.abandon`
- `oztowns.commands.add`
- `oztowns.commands.remove`
- `oztowns.commands.promote`
- `oztowns.commands.demote`
- `oztowns.commands.leave`
- `oztowns.commands.members`
- `oztowns.commands.transfer`
- `oztowns.commands.visualizer`

Bank nodes:

- `oztowns.commands.bank.deposit`
- `oztowns.commands.bank.withdraw`
- `oztowns.commands.bank.balance`
- `oztowns.commands.bank.help`

## Configuration

Primary config: `src/main/resources/config.yml`

Important sections:

- `storage.type` (`mysql` or `sqlite`)
- `mysql.*` MySQL connection (when using MySQL)
- `sqlite.file` SQLite DB filename in the plugin data folder (when using SQLite)
- `economy-plugin` economy backend (`ozanarchy-economy` or `vault`)
- `townmessages` and `townusercolor`
- `spawn-reminder.*`
- `town-creation-command`
- `spawn-delay`
- `cache.ttl-seconds`
- `towns.*` cost/upkeep values
- `unclaimable-worlds`
- `visualizer.*`
- `blacklisted-names`

Other configs:

- `messages.yml` - all user-facing messages/help text
- `gui.yml` - GUI layouts/items
- `holograms.yml` - DecentHolograms integration

## Placeholder / Hologram Integration

- PlaceholderAPI: auto-registers expansion when plugin is present.
- DecentHolograms: town spawn holograms update when enabled in config.

## Notes

- This plugin persists data in MySQL and creates required tables on startup.
- Keep `blacklisted-names` aligned with your community moderation policy.

## License

See `LICENESE` file
