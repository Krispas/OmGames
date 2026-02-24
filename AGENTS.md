# AGENTS.md

## Overview
This is a Minecraft Bukkit/Spigot plugin named OmGames. The active gameplay module is BedWars, with runtime logic, setup flow, and the shop/custom-item systems living under `src/main/java/krispasi/omGames/bedwars`.

## Key Paths
- `src/main/java/krispasi/omGames/OmGames.java`: plugin entrypoint; initializes config files and registers `/bw` + listeners.
- `src/main/java/krispasi/omGames/bedwars/BedwarsManager.java`: BedWars service layer; loads arenas/configs, manages the active session, merges rotating items, and coordinates stats/quick-buy.
- `src/main/java/krispasi/omGames/bedwars/game/GameSession.java`: match state machine and rules (generators, respawns, upgrades, scoreboards, shops).
- `src/main/java/krispasi/omGames/bedwars/listener/BedwarsListener.java`: gameplay event handling, custom items, GUI interactions.
- `src/main/java/krispasi/omGames/bedwars/setup/BedwarsSetupManager.java`: `/bw setup` workflow; writes arena data into `bedwars.yml`.
- `src/main/java/krispasi/omGames/bedwars/config/BedwarsConfigLoader.java`: parses `bedwars.yml` into arenas.
- `src/main/java/krispasi/omGames/bedwars/shop/*`: shop config + quick-buy handling.
- `src/main/java/krispasi/omGames/bedwars/item/*`: custom item definitions + loaders.
- `src/main/resources/*.yml`: default configs copied into the plugin data folder on first run.

## Config And Data
- BedWars configs live under `plugins/OmGames/Bedwars/` (created/migrated in `OmGames.ensureBedwarsConfig`).
  - `bedwars.yml`: arenas, spawns, beds, generators, event times.
  - `shop.yml`: base shop catalog.
  - `rotating-items.yml`: optional rotating shop additions; merged into `shop.yml` at load.
  - `custom-items.yml`: custom items and tuning.
- Persistent data is stored in SQLite under the plugin data folder:
  - `plugins/OmGames/quickbuy.db` for per-player quick-buy layouts.
  - `plugins/OmGames/bedwars-stats.db` for BedWars stats.
- `src/main/resources/plugin.yml` defines `/bw` and permissions.

## Conventions
- Prefer editing YAML configs for shop or item changes. Only change code if behavior is hardcoded.
- Gameplay or rule changes belong in `GameSession` or `BedwarsListener`.
- Setup flow changes should stay in `BedwarsSetupManager` and the config loader.
- `bedwars.yml` uses mixed-case sections (`Spawns`, `Generators`, `Shops`, `Base_Generators`). The loader accepts case variants, but keep new edits consistent with the setup manager casing.
- Avoid non-ASCII characters unless already present in the file being edited.

## Notes
- Maven project; shaded jar is produced and copied to a local server in `pom.xml`.
- No test runner is configured in this repo.
