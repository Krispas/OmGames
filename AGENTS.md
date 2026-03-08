# AGENTS.md

## 1) Purpose

This document is the operational handbook for agents working in `OmGames`.
Use it to decide where changes belong, how runtime state flows, and how to safely modify BedWars behavior without regressions.

Primary goal: keep BedWars gameplay stable while enabling fast config-first iteration.

## 2) Project Snapshot

- Project type: Bukkit/Paper plugin
- Active game mode: BedWars
- Main package: `krispasi.omGames`
- Java target: 21
- API target: Paper `1.21.11-R0.1-SNAPSHOT`
- Build tool: Maven (`mvn clean package`)
- Plugin main class: `krispasi.omGames.OmGames`
- Bukkit command root: `/bw`

## 3) Top-Level Layout

- `src/main/java/krispasi/omGames/OmGames.java`
  - Plugin bootstrap and shutdown.
  - Ensures BedWars config files exist in plugin data folder.
  - Loads managers/services and registers command + listener.

- `src/main/java/krispasi/omGames/bedwars/BedwarsManager.java`
  - BedWars service coordinator.
  - Loads arenas/shop/custom items/stat services/quick buy.
  - Owns active `GameSession` lifecycle.
  - Applies config migrations for `bedwars.yml`, `shop.yml`, `rotating-items.yml`, and `custom-items.yml`.

- `src/main/java/krispasi/omGames/bedwars/game/GameSession.java`
  - Match state machine and in-game rules.
  - Team assignment, generators, respawns, beds, upgrades, traps.
  - Shop purchase behavior, rotating items, world border, scoreboard.

- `src/main/java/krispasi/omGames/bedwars/listener/BedwarsListener.java`
  - Main event listener for BedWars.
  - Handles GUI clicks, combat checks, block rules, custom item interactions, projectile logic, death/respawn hooks, and anti-exploit controls.

- `src/main/java/krispasi/omGames/bedwars/setup/BedwarsSetupManager.java`
  - `/bw setup` authoring workflow.
  - Writes arena metadata back to `bedwars.yml`.

- `src/main/java/krispasi/omGames/bedwars/config/BedwarsConfigLoader.java`
  - Parser/validator for `bedwars.yml` arena model.

- `src/main/java/krispasi/omGames/bedwars/shop/*`
  - Shop model, loader, quick-buy persistence, purchase behaviors.

- `src/main/java/krispasi/omGames/bedwars/item/*`
  - Custom item model/loader and item metadata carriers.

- `src/main/java/krispasi/omGames/bedwars/stats/*`
  - Player stat persistence + lobby leaderboard display.

- `src/main/resources/*.yml`
  - Default embedded configs copied to plugin data folder on first run.

## 4) Runtime Lifecycle

### 4.1 onEnable Boot Sequence (`OmGames`)

1. Ensure/migrate configs into `<pluginData>/Bedwars/`:
   - `bedwars.yml`
   - `shop.yml`
   - `rotating-items.yml`
   - `custom-items.yml`
2. Construct `BedwarsManager`.
3. Load runtime data:
   - arenas
   - custom items
   - shop config (including rotating merge)
   - quick buy DB
   - stats DB
4. Start lobby leaderboard ticker.
5. Construct `BedwarsSetupManager`.
6. Register `/bw` executor/tab completer.
7. Register `BedwarsListener`.

### 4.2 onDisable

- `BedwarsManager.shutdown()`:
  - stops active session
  - stops leaderboard
  - closes quick-buy DB
  - closes stats DB
  - clears dropped items in loaded arena worlds

## 5) BedWars Service Ownership (Who Owns What)

- `BedwarsManager` owns:
  - arena catalog (`Map<String, Arena>`)
  - active `GameSession`
  - loaded `ShopConfig`
  - loaded `CustomItemConfig`
  - `QuickBuyService`
  - `BedwarsStatsService`
  - `BedwarsLobbyLeaderboard`

- `GameSession` owns match-scoped runtime data:
  - assignments, eliminated players/teams
  - bed states and tracked bed blocks
  - placed blocks and rollback items
  - respawn timers/protection
  - combat tags
  - team upgrades/traps
  - shop NPC tracking
  - scoreboard lifecycle
  - rotating item selection state

- `BedwarsListener` should delegate to `GameSession` for game logic whenever possible.

## 6) Command Surface (`/bw`)

Implemented in `BedwarsCommand`.

Public-use subcommands:
- `/bw stats`
- `/bw quick_buy` (also accepts `quick-buy`, and typo aliases `quck-buy`, `quck_buy`)

OP/admin subcommands (permission-gated):
- `/bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]`
- `/bw start`
- `/bw test start`
- `/bw stop`
- `/bw tp <arena>|lobby`
- `/bw lobby parkour <start|checkpoint [x]|end>`
- `/bw game out [player]`
- `/bw game spectate [player]`
- `/bw game join <team|spectate> [player]`
- `/bw game revive <team>`
- `/bw game skipphase`
- `/bw reload`
- `/bw setup new <arena>`
- `/bw setup <arena> [key]`

Permissions declared in `plugin.yml`:
- `omgames.bw.start`
- `omgames.bw.setup`
- `omgames.bw.reload`

Note: command implementation currently includes subcommands not fully reflected in `plugin.yml` usage text.

## 7) GameSession State Machine

`GameState` values:
- `IDLE`
- `LOBBY`
- `STARTING`
- `RUNNING`
- `ENDING`

Important entrypoints:
- `startLobby(plugin, initiator, lobbySeconds)`
- `start(plugin, initiator)`
- `start(plugin, initiator, countdownSeconds)`
- `stop()`

Operator/debug helpers:
- `forceJoin(player, team)`
- `reviveBed(team)`
- `skipNextPhase()`
- `addEditor/removeEditor`

High-signal rule APIs:
- `handlePlayerDeath`
- `handleRespawn`
- `handleBedDestroyed`
- `triggerRespawnBeacon`
- `handleWorldChange`
- `handlePlayerQuit`
- `handlePlayerJoin`

## 8) Data Folder Layout (Runtime)

All BedWars files live in:
- `plugins/OmGames/Bedwars/`

Files:
- `bedwars.yml`
- `shop.yml`
- `rotating-items.yml`
- `custom-items.yml`
- `quickbuy.db`
- `bedwars-stats.db`

Legacy migration behavior:
- If old files exist at `plugins/OmGames/<name>.yml`, they are moved into `plugins/OmGames/Bedwars/<name>.yml` on startup.

## 9) SQLite Schemas

### 9.1 quickbuy.db (`QuickBuyService`)

Table: `quick_buy`
- `player_uuid TEXT NOT NULL`
- `slot INTEGER NOT NULL`
- `item_id TEXT NOT NULL`
- PK: `(player_uuid, slot)`

Special marker:
- `__empty__` means intentional empty quick-buy slot.

### 9.2 bedwars-stats.db (`BedwarsStatsService`)

Table: `bedwars_stats`
- `player_uuid TEXT PRIMARY KEY`
- `wins INTEGER NOT NULL`
- `kills INTEGER NOT NULL`
- `deaths INTEGER NOT NULL`
- `final_kills INTEGER NOT NULL`
- `final_deaths INTEGER NOT NULL`
- `games_played INTEGER NOT NULL`
- `beds_broken INTEGER NOT NULL`
- `parkour_best_time_ms INTEGER NOT NULL` (default `-1`)
- `parkour_best_checkpoint_uses INTEGER NOT NULL` (default `0`)

Service auto-adds missing columns for legacy DBs (`deaths`, `final_kills`, `final_deaths`, `parkour_best_time_ms`, `parkour_best_checkpoint_uses`).

## 10) Config Guide

## 10.1 `bedwars.yml`

Root keys:
- `leaderboard`
- `parkour-leaderboard`
- `lobby-parkour`
- `arenas`

Leaderboard accepted forms:
- Flat string: `"world x y z"` or `"x y z"`
- Section:
  - `leaderboard.world`
  - `leaderboard.x`
  - `leaderboard.y`
  - `leaderboard.z`
  - optional `leaderboard.location` fallback parser

Arena keys (canonical):
- `world`
- `center`
- `center-radius`
- `game-lobby`
- `map-lobby`
- `corner_1`
- `corner_2`
- `base-radius`
- `anti-build.base-generator-radius`
- `anti-build.advanced-generator-radius`
- `beds`
- `Generators` or `generators`
- `Base_Generators` (canonical setup output)
- `Spawns` (canonical setup output)
- `Shops` (canonical setup output)
- `generator-settings`
- `event-times`

Arena string formats:
- Block point: `x y z`
- Bed pair: `headX headY headZ, footX footY footZ`
- Shop location: `x y z yaw`
  - yaw supports numeric or cardinal (`NORTH`, `SOUTH`, `EAST`, `WEST`, and short forms)

Generator naming conventions:
- Base: `base_gen_<team>` (also tolerant of some legacy aliases)
- Diamond: `diamond_1`, `diamond_2`, ...
- Emerald: `emerald_1`, `emerald_2`, ...

Spawn keys:
- `base_<team>` under `Spawns`

Shop keys:
- Under `Shops.<team>.main`
- Under `Shops.<team>.upgrades`

`generator-settings` expected list lengths:
- `base-forge.iron.intervals-seconds`: 5
- `base-forge.iron.amounts`: 5
- `base-forge.iron.caps`: 5
- `base-forge.gold.intervals-seconds`: 5
- `base-forge.gold.amounts`: 5
- `base-forge.gold.caps`: 5
- `base-forge.emerald.intervals-seconds`: 5
- `base-forge.emerald.amounts`: 5
- `base-forge.emerald.caps`: 5
- `diamond.intervals-seconds`: 3
- `emerald.intervals-seconds`: 3

`event-times` fields (seconds):
- `tier-2`
- `tier-3`
- `bed-destruction`
- `sudden-death`
- `game-end`

### 10.2 `shop.yml`

Root: `shop`

Sections:
- `shop.categories`
- `shop.items`

Category fields:
- `title`
- `icon`
- `size` (normalized to 9..54 and multiple of 9)
- `entries` (`itemId: slot`)

Item fields (common):
- `material`
- `amount`
- `cost.material`
- `cost.amount`
- `display-name`
- `lore`
- `behavior`
- `tier`
- `team-color`
- `custom-item`
- `knockback-bonus`
- `disable-after-sudden-death`
- `limit.scope` (`PLAYER` or `TEAM`)
- `limit.amount`
- `enchants`
- `potion-effects`

`knockback-bonus` notes:
- Optional decimal extra melee velocity applied by listener-side combat handling.
- Intended for cases where vanilla enchant levels are too coarse (for example, approximating "Knockback 1.5").

Special item fields:
- Fireworks (`material: FIREWORK_ROCKET`):
  - `firework.power`
  - `firework.effect.type`
  - `firework.effect.colors`
  - `firework.effect.fade-colors`
  - `firework.effect.flicker`
  - `firework.effect.trail`
  - `firework.explosion.power`
  - `firework.explosion.damage`
  - `firework.explosion.knockback`

Position override support:
- item-local:
  - `position.line`
  - `position.column` (or typo fallback `position.collum`)

Material aliases normalized by loader:
- `GOLD` -> `GOLD_INGOT`
- `IRON` -> `IRON_INGOT`

Behavior inference can be derived from category/material if omitted.

### 10.3 `rotating-items.yml`

Same schema as `shop.yml` and merged into the base shop config at load.

Category split:
- `shop.categories.rotating`
  - rotating item-shop entries only
- `shop.categories.rotating_upgrades`
  - rotating team-upgrade entries used by upgrade availability/rotation selection
  - not intended as a normal item-shop tab

Rotating item field notes:
- `disable-after-sudden-death`
  - optional boolean on rotating entries
  - if `true`, the entry is unavailable once sudden death starts
  - item displays should show a red lore warning

Merge behavior:
- `ShopConfig.merge(base, rotating)`
- Rotating categories/items augment base shop entries.

Migration behavior in manager:
- separates legacy mixed rotating upgrade entries into `rotating_upgrades`
- prunes invalid legacy rotating upgrades
- ensures `totem_of_undying` exists with default lore + limit

### 10.4 `custom-items.yml`

Root: `custom-items`

Definition fields:
- `type`
- `material`
- `velocity`
- `yield`
- `incendiary`
- `damage`
- `knockback`
- `lifetime-seconds`
- `health`
- `speed`
- `range`
- `uses`
- `max-blocks`
- `bridge-width`

Supported `type` values:
- `FIREBALL`
- `BRIDGE_EGG`
- `BED_BUG`
- `DREAM_DEFENDER`
- `CRYSTAL`
- `HAPPY_GHAST`
- `RESPAWN_BEACON`
- `FLAMETHROWER`
- `BRIDGE_BUILDER`
- `CREEPING_ARROW`
- `TACTICAL_NUKE`
- `BRIDGE_ZAPPER`
- `PORTABLE_SHOPKEEPER`
- `MAGIC_MILK`

Loader validation notes:
- `bridge-width` coerced to odd and minimum 1.
- `max-blocks` clamped to non-negative default behavior.
- IDs normalized to lowercase.

## 11) Setup Workflow (`/bw setup`)

Manager: `BedwarsSetupManager`

Create arena:
- `/bw setup new <arenaId>`
- seeds default sections for all teams and generator/event defaults

Status:
- `/bw setup <arenaId>` prints missing/completed keys

Apply key:
- `/bw setup <arenaId> <key>`

Accepted key families include:
- `world`
- `center`
- `center-radius <int>`
- `corner_1`
- `corner_2`
- `base-radius <int>`
- `anti-build.base-generator-radius <int>`
- `anti-build.advanced-generator-radius <int>`
- `game-lobby`
- `map-lobby`
- `<team>.bed`
- `<team>.spawn`
- `<team>.base-gen`
- `<team>.shop`
- `<team>.upgrades`
- `generator.diamond.<n>`
- `generator.emerald.<n>`

Bed setup behavior:
- First call stores one bed block position.
- Second call stores paired block.
- If player is standing on actual bed block, setup auto-detects head/foot and writes both immediately.

## 12) Code Placement Rules (Critical)

Config-first preference:
- If balance/item tuning can be done in YAML, edit YAML instead of Java.

Where to put gameplay logic:
- Match rules and lifecycle -> `GameSession`
- Event gating and Bukkit event translation -> `BedwarsListener`
- Setup command behavior -> `BedwarsSetupManager`
- Parsing and backward compatibility for `bedwars.yml` -> `BedwarsConfigLoader`
- Shop parsing/behavior metadata -> `shop/*`
- Custom item config parsing -> `item/*`

Do not scatter BedWars rules into `OmGames` main plugin class.

## 13) High-Risk Areas and Invariants

- Active session ownership:
  - Only one active `GameSession` should exist in `BedwarsManager`.
  - Starting a new session stops previous session first.

- Session stop safety:
  - Ensure every created task/entity/scoreboard state is cleaned on `stop()`.

- Team keys:
  - Team lookup is case-insensitive by `TeamColor.fromKey`, but config should remain canonical lowercase keys (`red`, `blue`, etc).

- Config casing:
  - Loader accepts casing variants (`Spawns`/`spawns`, `Generators`/`generators`, `Base_Generators` variants).
  - Keep newly written keys consistent with setup manager canonical names.

- Shop/rotating merge:
  - Changes to `shop.yml` and `rotating-items.yml` are both active after reload.

- Stats writes:
  - Winning team gets wins only when stats are enabled for the session.

## 14) Typical Change Recipes

### 14.1 Rebalance a shop item

1. Edit `shop.yml` (`shop.items.<id>` cost/amount/lore/behavior).
2. Ensure category entry exists under `shop.categories.<category>.entries`.
3. Reload via `/bw reload`.
4. Verify purchase, price, and inventory behavior in-game.

### 14.2 Add a rotating item

1. Add item under `rotating-items.yml -> shop.items.rotating.<id>`.
2. Add slot under `shop.categories.rotating.entries.<id>`.
3. If custom behavior needed, define `custom-item` and ensure matching definition exists in `custom-items.yml`.
4. `/bw reload`, then open rotating category and validate.

### 14.3 Tune custom item physics/damage

1. Edit `custom-items.yml` definition.
2. Keep `type` unchanged unless behavior class intentionally changes.
3. `/bw reload`.
4. Validate projectile spawn, impact, cooldown interactions, and edge cases (void, world change, death cleanup).

### 14.4 Modify generator pacing

1. Edit `bedwars.yml -> arenas.<id>.generator-settings`.
2. Preserve expected list lengths.
3. Reload and verify spawn cadence in live match.

### 14.5 Change respawn or event timing

1. Edit `bedwars.yml -> arenas.<id>.event-times`.
2. Values are seconds.
3. Start test match and verify phase broadcasts and state transitions.

### 14.6 Add or repair arena setup

1. Use `/bw setup new <arena>` if new.
2. Fill required points with `/bw setup <arena> <key>`.
3. Confirm all teams have bed/spawn/base generator/shop entries.
4. Use `/bw tp <arena>` and run a dry match.

## 15) Build and Validation

Build:
- `mvn clean package`

Minimal validation pass after gameplay edits:
1. Plugin starts without stack traces.
2. `/bw start` map select opens.
3. Team assignment or team pick starts match.
4. Beds break and elimination logic behaves correctly.
5. Shop purchase paths work (quick-buy + category + upgrades).
6. At least one custom item used successfully.
7. `/bw reload` works mid-server without corrupting session manager state.
8. `/bw stop` fully cleans up runtime artifacts.

Validation after setup/config edits:
1. `bedwars.yml` loads all intended arenas.
2. Teleport (`/bw tp <arena>`) lands in expected lobby/center fallback.
3. Shops face expected direction using yaw/cardinal strings.
4. Generator locations and anti-build radius are honored.

Validation for persistence:
1. Quick-buy edits persist after reconnect/restart.
2. Stats increment and leaderboard updates near anchor.
3. Parkour finish best-time/checkpoint records persist to `bedwars-stats.db`.

## 16) Troubleshooting Quick Notes

- "No arenas configured":
  - `bedwars.yml` missing/invalid `arenas` section or parse failures.

- "World not loaded":
  - arena world name exists in config but world is not loaded by server.

- Shop item not visible:
  - missing category entry slot, invalid material, or parsed item dropped by loader warning.

- Custom item not firing:
  - missing `custom-item` link on shop item, or missing matching id in `custom-items.yml`.

- Leaderboard not visible:
  - no nearby non-spectator players, anchor world not resolved, or no stats rows yet.

## 17) Contributor Checklist for Agents

Before finishing work:
1. Change is in correct ownership file.
2. No hardcoded balance values were added when config can own them.
3. Backward compatibility preserved for existing config casing/aliases where relevant.
4. Runtime cleanup paths remain intact (tasks/entities/scoreboards).
5. Command or setup UX text updated if behavior changed.
6. Manual validation steps were run or explicitly called out as not run.
7. `AGENTS.md` was updated if config schema, operational workflow, or project-specific agent rules changed.

## 18) Short Rule of Thumb

- Prefer config edits for tuning.
- Prefer `GameSession` for rules.
- Prefer `BedwarsListener` for event plumbing.
- Keep setup/config parser tolerant of legacy input.
- Never leave scheduled tasks or spawned entities unmanaged at session end.


## 19) OmVeinsAPI

- DO NOT TOUCH THAT CODE in Omveins API.
- Do not use this API during server startup!
