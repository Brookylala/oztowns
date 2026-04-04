# OzTowns 2.0

OzTowns is a Paper town-management plugin focused on town ownership, land claims, banking/upkeep, tier progression, and raid gameplay.
This branch reflects the 2.0 direction: modular services, split feature configs, and state-aware systems.

## Features

- Town lifecycle: create, rename, delete/abandon, set spawn, town teleport.
- Chunk claiming: claim/unclaim with adjacency rules and world blacklist support.
- Member management: invites, join/deny flow, roles (Mayor/Officer/Member), transfer mayor.
- Town permissions: per-member town permission nodes (GUI + backend checks).
- Bank system: deposits, withdrawals, balance, optional bank cap scaling by tier.
- Upkeep system: scheduled payments, inactivity checks, state transitions, optional decay cleanup.
- Tier system: default tier, max tier, progress-driven upgrades, optional downgrades, perk modifiers.
- Raids 2.0 flow: OzTowns raid lockpick item + GUI minigame entry + state-based raid behavior.
- GUI menus: main menu, town info, members, member permissions, and bank GUI.
- Optional integrations: PlaceholderAPI placeholder expansion and DecentHolograms spawn holograms.
- Multi-file configuration + message packs under dedicated folders.

## 2.0 Architecture Direction

OzTowns 2.0 is organized around feature services and config managers rather than one monolithic handler.
Core examples in current code:

- `TownTierService` + `TownTierPerkService`
- `UpkeepService` + upkeep helper services/repositories
- `RaidService` + `RaidConfigManager` + minigame service/session types
- Dedicated config handlers for main, town, upkeep, tiers, holograms, protection, raid, and GUI

## Requirements

- Java: 21
- Server API: Paper/Spigot `1.21.x` (`api-version: 1.21`, built against `1.21.11-R0.1-SNAPSHOT`)
- Database backends:
  - SQLite (default)
  - MySQL
- Economy provider (configurable in `config.yml`):
  - `ozanarchy-economy`
  - Vault
- Optional plugins:
  - PlaceholderAPI
  - DecentHolograms

## Installation

1. Build the plugin jar (or use a built release jar).
2. Put `OzTowns-2.0.jar` into your server `plugins/` folder.
3. Start the server once to generate configs.
4. Configure files in `plugins/OzTowns/`.
5. Restart (or use admin reload command).

## Configuration Overview

Current config layout in this branch:

- `config.yml`: global toggles (`features.*`), storage, economy provider, cache settings.
- `town-config.yml`: claim cost/rules, spawn settings, town chat, member limits, visualizer.
- `protection-config.yml`: claim protection rules and bypass options.
- `upkeep.yml`: upkeep intervals/costs, activity checks, state thresholds, decay, tier integration.
- `tiers.yml`: tier definitions, progression thresholds, global perk caps, per-tier perk bonuses.
- `raid-config.yml`: raid entry item, minigame, state behavior, cooldown, payout, recipe.
- `holograms.yml`: DecentHolograms spawn-hologram rendering settings.
- `gui/`: GUI layout files (`main.yml`, `info.yml`, `members.yml`, `permissions.yml`, `bank.yml`).
- `messages/`: split message files (`general.yml`, `town.yml`, `raid.yml`, `bank.yml`, `upkeep.yml`, `admin.yml`, `help.yml`).

## Commands

Primary commands from `plugin.yml`:

- `/towns` (`/town`, `/oztowns`, `/towny`)
- `/townbank` (`/tbank`, `/townsbank`)
- `/townadmin` (`/tadmin`)
- `/tm`

Common `/towns` subcommands:

- `create`, `rename`, `claim`, `unclaim`
- `setspawn`, `spawn`
- `add|invite`, `accept`, `deny`, `remove`
- `promote`, `demote`, `setmayor`, `leave`
- `members`, `info [town]`, `visualizer|chunks`, `abandon confirm`

Common `/townbank` subcommands:

- `deposit`, `withdraw`, `balance|bal`, `gui`

Common `/townadmin` subcommands:

- `reload`, `delete`, `setspawn`, `removespawn`, `spawn`
- `add`, `remove`, `setmayor`, `givelockpick`

## Permissions

Main permission nodes (from `plugin.yml`):

- `oztowns.commands` (+ granular `oztowns.commands.*` nodes)
- `oztowns.commands.info`
- `oztowns.commands.bank` (+ `deposit`, `withdraw`, `balance`, `help`)
- `oztowns.admin`
- `oztowns.admin.protectionbypass`

## Raids (Current 2.0 Behavior)

Raid entry is OzTowns-owned in current code:

- Player right-clicks a town spawn Lodestone with a configured OzTowns raid lockpick item.
- Optional GUI reaction-timing minigame runs (`raid.minigame.*`).
- On success, raid continues into timer/payout flow; on fail/timeout/cancel, attempt ends.

State-aware behavior is config-driven via `raid.state-behavior.<STATE>` and uses upkeep state:

- States: `ACTIVE`, `OVERDUE`, `NEGLECTED`, `ABANDONED`, `DECAYING`
- Per-state controls: mode (`NORMAL`/`CLEANUP`), timer, alerts, payout mode/percent, delete-on-success.
- `DECAYING` defaults to cleanup-style flow.

Other raid controls currently present:

- Per-town raid cooldown (persisted in DB)
- Fail-on-damage / death / disconnect toggles
- Optional lockpick crafting recipe
- Tier-based minigame difficulty overrides (`TIER_1` -> `TIER_5`)

## Tiers

Towns have a tier key + progress persisted in DB (`towns.tier`, `tier_progress`, `tier_streak`).

Current behavior:

- New towns start at `tiers.default-tier`.
- Upkeep success can add tier progress (configurable).
- Automatic upgrades are processed by `TownTierService` when thresholds are met.
- Progress carry-over is supported (extra progress rolls into next tier).
- Upgrades are capped by `tiers.max-tier`.

Tier perks currently wired into gameplay:

- Claim cost discount
- Upkeep cost modifier
- Bank cap bonus
- Claim cap bonus (with global base cap + per-tier bonus model)

Tier visibility in GUI:

- Main town GUI includes a glanceable tier item (tier name, current progress/required, upkeep state).
- `/town info` GUI includes detailed tier panel (current tier, progress, required, next tier).

## Upkeep

Upkeep runs on a scheduler and processes town snapshots each cycle.

Current behavior includes:

- Optional auto-pay from town bank
- Unpaid-cycle + inactivity tracking
- State progression: `ACTIVE -> OVERDUE -> NEGLECTED -> ABANDONED -> DECAYING`
- Optional decay actions (claim decay and eventual town removal)
- Tier integration (progress gain/loss + optional downgrade rules)

## Developer Notes

- Build command:
  - `mvn -DskipTests package`
- Main package root:
  - `src/main/java/net/ozanarchy/towns/`
- Resources/config root:
  - `src/main/resources/`
- This branch is the active 2.0 rework direction; expect iterative cleanup and tuning around modular services/configs.

## License

This project is licensed under the GNU General Public License v3.0.
See [LICENSE](LICENSE) for full terms.
