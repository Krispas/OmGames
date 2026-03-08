# AGENTS.md

## 1) Repo Rules

### 1.1 Purpose

This file is the operational handbook for agents working in `OmGames`.
Update `AGENTS.md` if project-specific workflow or schema changed.
Read it before changing code. Keep it updated when project-specific workflow, config schema, or runtime rules change.

Primary goal: keep BedWars stable while allowing fast config-first iteration.

### 1.2 Project Snapshot

- Project type: Bukkit/Paper plugin
- Main package: `krispasi.omGames`
- Active game mode: BedWars
- Java target: 21
- API target: Paper `1.21.11-R0.1-SNAPSHOT`
- Build tool: Maven (`mvn clean package`)
- Plugin main class: `krispasi.omGames.OmGames`
- Bukkit command root: `/bw`

### 1.3 Global Rules

- Prefer config edits over Java changes when the change is balance or content tuning.
- Do not add automatic config or file migration logic.
- If defaults need to change, update the resource files in `src/main/resources/`.
- If an existing server config needs the new defaults, the expected workflow is to delete that file and let the plugin recreate it.
- Do not touch `OmVeinsAPI`.
- Do not use `OmVeinsAPI` during server startup.

## 2) BedWars

### 2.1 Top-Level Layout

- `src/main/java/krispasi/omGames/OmGames.java`
  - Plugin bootstrap and shutdown.
  - Ensures BedWars config files exist in the plugin data folder.
  - Loads managers/services and registers the command and listener.

- `src/main/java/krispasi/omGames/bedwars/BedwarsManager.java`
  - BedWars service coordinator.
  - Loads arenas, shop config, custom items, stats, quick-buy, and leaderboards.
  - Owns the single active `GameSession`.

- `src/main/java/krispasi/omGames/bedwars/game/GameSession.java`
  - Match state machine and main BedWars rules owner.
  - Handles gameplay, rotating items, match events, upgrades, beds, respawns, and scoreboards.

- `src/main/java/krispasi/omGames/bedwars/listener/BedwarsListener.java`
  - BedWars event plumbing.
  - Handles Bukkit events and delegates actual game rules into `GameSession`.

- `src/main/java/krispasi/omGames/bedwars/setup/BedwarsSetupManager.java`
  - `/bw setup` workflow.
  - Writes arena metadata back to `bedwars.yml`.

- `src/main/java/krispasi/omGames/bedwars/config/BedwarsConfigLoader.java`
  - Parser/validator for `bedwars.yml`.

- `src/main/java/krispasi/omGames/bedwars/shop/*`
  - Shop model, loader, quick-buy persistence, purchase behavior.

- `src/main/java/krispasi/omGames/bedwars/item/*`
  - Custom item model, loader, and item metadata.

- `src/main/java/krispasi/omGames/bedwars/stats/*`
  - BedWars stat persistence and lobby leaderboard display.

- `src/main/java/krispasi/omGames/bedwars/lobby/BedwarsLobbyParkour.java`
  - Lobby parkour runtime.
  - Tracks checkpoints, control items, and next-target guidance.

- `src/main/resources/*.yml`
  - Default configs copied into the plugin data folder on first run.

### 2.2 Runtime Lifecycle

#### 2.2.1 onEnable

1. Ensure these files exist inside `plugins/OmGames/Bedwars/`:
   - `bedwars.yml`
   - `shop.yml`
   - `rotating-items.yml`
   - `custom-items.yml`
2. Construct `BedwarsManager`.
3. Load:
   - arenas
   - custom items
   - shop config
   - rotating config merge
   - quick-buy DB
   - stats DB
4. Start lobby and parkour leaderboards.
5. Construct `BedwarsSetupManager`.
6. Register `/bw`.
7. Register `BedwarsListener`.

#### 2.2.2 onDisable

- `BedwarsManager.shutdown()`:
  - stops active session
  - stops leaderboards
  - shuts down quick-buy DB
  - shuts down stats DB
  - clears dropped items in loaded arena worlds

### 2.3 Ownership Rules

- `BedwarsManager` owns:
  - arena catalog
  - active `GameSession`
  - `ShopConfig`
  - `CustomItemConfig`
  - `QuickBuyService`
  - `BedwarsStatsService`
  - lobby/parkour leaderboards

- `GameSession` owns match-scoped runtime:
  - assignments
  - eliminated players and teams
  - beds and bed blocks
  - placed blocks and rollback items
  - respawns and respawn protection
  - combat tags
  - team upgrades and traps
  - shop NPCs
  - sidebar/scoreboard state
  - rotating selection
  - match events

- `BedwarsListener` should stay as Bukkit event translation and call into `GameSession` for rules.

- `BedwarsLobbyParkour` owns lobby parkour runtime:
  - active run state
  - checkpoint progression
  - temporary hotbar control items
  - direction compass target updates

### 2.4 Command Surface

Implemented in `BedwarsCommand`.

Public subcommands:
- `/bw stats`
- `/bw quick_buy`

Admin subcommands:
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
- `/bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]`

Permissions declared in `plugin.yml`:
- `omgames.bw.start`
- `omgames.bw.setup`
- `omgames.bw.reload`

### 2.5 GameSession State Machine

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

Operator helpers:
- `forceJoin(player, team)`
- `reviveBed(team)`
- `skipNextPhase()`
- `addEditor(player)`
- `removeEditor(player)`

High-signal rule APIs:
- `handlePlayerDeath`
- `handleRespawn`
- `handleBedDestroyed`
- `triggerRespawnBeacon`
- `handleWorldChange`
- `handlePlayerQuit`
- `handlePlayerJoin`

### 2.6 Runtime Data Layout

BedWars runtime files live in:
- `plugins/OmGames/Bedwars/`

Files:
- `bedwars.yml`
- `shop.yml`
- `rotating-items.yml`
- `custom-items.yml`
- `quickbuy.db`
- `bedwars-stats.db`

No automatic config/file migration is expected.

### 2.7 SQLite

#### 2.7.1 `quickbuy.db`

Table: `quick_buy`
- `player_uuid TEXT NOT NULL`
- `slot INTEGER NOT NULL`
- `item_id TEXT NOT NULL`
- PK: `(player_uuid, slot)`

Special marker:
- `__empty__` means intentional empty quick-buy slot.

#### 2.7.2 `bedwars-stats.db`

Table: `bedwars_stats`
- `player_uuid TEXT PRIMARY KEY`
- `wins INTEGER NOT NULL`
- `kills INTEGER NOT NULL`
- `deaths INTEGER NOT NULL`
- `final_kills INTEGER NOT NULL`
- `final_deaths INTEGER NOT NULL`
- `games_played INTEGER NOT NULL`
- `beds_broken INTEGER NOT NULL`
- `parkour_best_time_ms INTEGER NOT NULL`
- `parkour_best_checkpoint_uses INTEGER NOT NULL`

### 2.8 Config Guide

#### 2.8.1 `bedwars.yml`

Root keys:
- `leaderboard`
- `parkour-leaderboard`
- `match-events`
- `lobby-parkour`
- `arenas`

`match-events` fields:
- `enabled`
- `chance-percent`
- `events.<event-id>.weight`

Supported event ids:
- `speedrun`
- `benevolent-upgrades`
- `long-arms`
- `moon-big`
- `blood-moon`
- `in-this-economy`
- `april-fools`

Arena timing fields:
- `event-times.tier-2`
- `event-times.tier-3`
- `event-times.bed-destruction`
- `event-times.sudden-death`
- `event-times.game-end`

#### 2.8.2 `shop.yml`

Root:
- `shop.categories`
- `shop.items`

Common item fields:
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
- `limit.scope`
- `limit.amount`
- `enchants`
- `potion-effects`

Notes:
- `knockback-bonus` adds an `ATTACK_KNOCKBACK` item attribute modifier on the held weapon.
- Use config changes for shop balancing first.

#### 2.8.3 `rotating-items.yml`

Same schema as `shop.yml`.

Categories:
- `shop.categories.rotating`
  - rotating shop items
- `shop.categories.rotating_upgrades`
  - rotating team upgrades
  - not meant to be a normal visible shop tab

Rotating item notes:
- `disable-after-sudden-death: true`
  - blocks that entry after sudden death
  - UI should show a red warning lore

Merge behavior:
- `ShopConfig.merge(base, rotating)`

#### 2.8.4 `custom-items.yml`

Root:
- `custom-items`

Common definition fields:
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
- `ABYSSAL_RIFT`
- `ELYTRA_STRIKE`

Behavior notes:
- `FLAMETHROWER`
  - cone attack in front of the player
  - uses particles for the area preview and directly damages/ignites targets in the cone
- `ABYSSAL_RIFT`
  - fixed deployable aura
  - uses model `om:rift1`
  - has separate hitbox/display/nameplate entities
  - health/range come from config
- `ELYTRA_STRIKE`
  - instant effect purchase
  - equips temporary Elytra, teleports above team spawn, and cleans up on landing/death/quit/session end

### 2.9 Match Event Workflow

Prestart event control lives in the team-assign menu:
- left-click `Game Events` to enable or disable events for that match
- right-click `Game Events` to force a specific event or switch back to `Auto Random`

Forced event selection is prestart-only state and should not be conflated with the runtime `activeMatchEvent`.

### 2.10 Setup Workflow

Manager: `BedwarsSetupManager`

Create arena:
- `/bw setup new <arenaId>`

Status:
- `/bw setup <arenaId>`

Apply keys:
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

### 2.11 Placement Rules

Put changes here:
- match rules and lifecycle -> `GameSession`
- event plumbing -> `BedwarsListener`
- setup command behavior -> `BedwarsSetupManager`
- `bedwars.yml` parsing -> `BedwarsConfigLoader`
- shop parsing/model -> `shop/*`
- custom item parsing/model -> `item/*`

Do not push BedWars rules into `OmGames`.

### 2.12 High-Risk Invariants

- Only one active `GameSession` should exist at a time.
- `stop()` cleanup must remove tasks, entities, sidebars, displays, and match-only state.
- Team lookup is case-insensitive through `TeamColor.fromKey`, but config keys should stay canonical lowercase.
- Keep BedWars config parsing tolerant of legacy casing and aliases where the loaders already support them.
- Shop + rotating config are both active after reload because of merge behavior.
- If stats are disabled for a session, progression/wins should not be awarded.

### 2.13 Common Recipes

#### 2.13.1 Rebalance a shop item

1. Edit `shop.yml`.
2. Confirm category entry slot exists.
3. `/bw reload`
4. Test the purchase path in game.

#### 2.13.2 Add a rotating item

1. Add the item to `rotating-items.yml`.
2. Add the category entry under `shop.categories.rotating.entries`.
3. If needed, add a linked custom item in `custom-items.yml`.
4. `/bw reload`
5. Validate in the rotating shop tab.

#### 2.13.3 Tune a custom item

1. Edit `custom-items.yml`.
2. Keep the same `type` unless behavior is intentionally changing.
3. `/bw reload`
4. Test spawn, impact, cleanup, and edge cases.

#### 2.13.4 Tune match events

1. Edit `bedwars.yml -> match-events`.
2. Adjust `enabled`, `chance-percent`, and weights.
3. Start a match from the team menu.
4. Verify toggle, force-select, title, and event effects.

### 2.14 Validation

After gameplay edits:
1. Plugin starts without stack traces.
2. `/bw start` opens map select.
3. Team setup starts a match correctly.
4. Beds, elimination, respawns, and phase changes still work.
5. Shop purchase paths work.
6. At least one custom item works.
7. `/bw reload` still works.
8. `/bw stop` cleans everything up.

After config edits:
1. Relevant file reloads without warnings.
2. Config-driven items/upgrades/events appear where expected.
3. Existing runtime flows still behave correctly.

### 2.15 Troubleshooting

- `No arenas configured`
  - invalid or missing `arenas` section in `bedwars.yml`

- `World not loaded`
  - arena world exists in config but is not loaded on the server

- shop item missing
  - missing category entry, invalid material, or loader dropped the entry

- custom item not working
  - missing `custom-item` link or missing matching definition in `custom-items.yml`

- forced event not applying
  - verify the match was started from the same prestart session where the force selection was made
  - verify events are not disabled for that match

### 2.16 Contributor Checklist

Before finishing:
1. Put the change in the correct ownership file.
2. Prefer config over Java for tuning.
3. Do not add migration logic.
4. Preserve cleanup paths.
5. Update command or UI text if behavior changed.
6. Run validation or explicitly state what was not run.
