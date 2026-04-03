# OzTowns Update Notes

This file is a quick summary of the recent OzTowns changes.

## What Was Updated

- Integrated OzTowns with the newer OminousChestLock API/event surface.
- Kept OminousChestLock optional at runtime (OzTowns still works without it).
- Added a dedicated `raid-config.yml` for raid mechanics and raid-item settings.
- Moved raid text/messages responsibility back to `messages.yml`.
- Removed legacy raid-config fallback behavior (raid settings now come from `raid-config.yml`).
- Added an OzTowns-owned local raid item model and validation logic.
- Added a configurable lockpick crafting recipe registration flow with safe validation and duplicate-safe reload handling.
- Added dedicated config handlers with automatic backup/recovery for all plugin config files.

## Config Backup/Recovery System

New behavior now covers all plugin configs:

- `config.yml`
- `gui.yml`
- `messages.yml`
- `holograms.yml`
- `protection-config.yml`
- `raid-config.yml`

What it does:

- Uses dedicated handler classes per config file.
- Uses a reusable base YAML handler so backup/recovery logic is centralized.
- Creates/maintains backups in `plugins/OzTowns/backups/`.
- Keeps a rolling latest backup: `<config>.latest.yml`.
- Creates snapshot backups on reload/save: `<config>.<timestamp>.bak.yml`.
- Detects corrupted YAML on load and attempts restore from backup automatically.
- If restore is not possible, recreates from bundled defaults without crashing.
- Stores corrupted file copies in `backups/corrupt/` before recovery/recreate.
- Logs clear events for backup creation, restore, corruption handling, and fallback recreation.

## Lockpick Crafting Recipe Added

Recipe behavior:

- Reads from `raid-config.yml` under top-level `recipe`.
- Uses a fixed `NamespacedKey` of `oztowns_raid_lockpick`.
- Removes existing recipe and re-registers on startup/reload to avoid duplicates.
- Reuses the same lockpick item builder (including configured metadata and optional PDC) used by admin-give.
- Validates shape and ingredient mappings safely and logs warnings without crashing when invalid.

Default config block:

```yml
recipe:
  enabled: true
  shape:
    - " IN"
    - " SI"
    - "S  "
  ingredients:
    I: IRON_INGOT
    N: NETHERITE_SCRAP
    S: STICK
```

## Files Touched (High Level)

- `src/main/java/net/ozanarchy/towns/config/BaseYamlConfigHandler.java`
- `src/main/java/net/ozanarchy/towns/config/MainConfigHandler.java`
- `src/main/java/net/ozanarchy/towns/config/GuiConfigHandler.java`
- `src/main/java/net/ozanarchy/towns/config/MessagesConfigHandler.java`
- `src/main/java/net/ozanarchy/towns/config/HologramsConfigHandler.java`
- `src/main/java/net/ozanarchy/towns/config/ProtectionConfigHandler.java`
- `src/main/java/net/ozanarchy/towns/config/RaidConfigFileHandler.java`
- `src/main/java/net/ozanarchy/towns/raid/RaidConfigManager.java`
- `src/main/java/net/ozanarchy/towns/raid/RaidRecipeManager.java`
- `src/main/java/net/ozanarchy/towns/events/AdminEvents.java`
- `src/main/java/net/ozanarchy/towns/TownsPlugin.java`
- `src/main/resources/raid-config.yml`
- `README-UPDATES.md`

## Build Status

- `mvn -DskipTests package` succeeds.
