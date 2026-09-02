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
- Java target: 25
- API target: Paper `26.2.build.121-stable`
- Build tool: Maven (`mvn clean package`)
- Plugin main class: `krispasi.omGames.OmGames`
- Bukkit command root: `/bw`

### 1.3 Global Rules

- Prefer config edits over Java changes when the change is balance or content tuning.
- Do not add automatic config or file migration logic.
- Do not create or grow monolithic classes.
- If a class is approaching roughly `2000` lines, split it by responsibility before adding more code.
- Prefer extracting support/runtime/helper classes by concern while preserving ownership boundaries and cleanup invariants.
- If defaults need to change, update the resource files in `src/main/resources/`.
- If an existing server config needs the new defaults, the expected workflow is to delete that file and let the plugin recreate it.
- Target Paper `26.2` only; do not keep backward-compatibility shims for older Minecraft/Paper versions.
- Preserve saved data compatibility (configs/SQLite/player data) so existing servers can be updated without data loss.
- Prefer native Bukkit/Paper APIs; only use reflection when no public API exists and the cost is justified.
- Do not touch `OmVeinsAPI`.
- Do not use `OmVeinsAPI` during server startup.
- If you copy Bukkit/Paper or similar external reference files into the repo for implementation reference, delete those copied reference files before finishing.

## 2) BedWars

### 2.1 Top-Level Layout

- `src/main/java/krispasi/omGames/OmGames.java`
  - Plugin bootstrap and shutdown.
  - Ensures BedWars config files exist in the plugin data folder.
  - Loads managers/services and registers the command and listener.

- `src/main/java/krispasi/omGames/bedwars/BedwarsManager.java`
  - BedWars service coordinator.
  - Loads arenas, shop config, custom items, stats, quick-buy, karma, and leaderboards.
  - Owns the single active `GameSession`.

- `src/main/java/krispasi/omGames/bedwars/game/GameSession.java`
  - Match state machine and main BedWars rules owner.
  - Handles gameplay, rotating items, match events, upgrades, beds, respawns, and scoreboards.

- `src/main/java/krispasi/omGames/bedwars/game/GameSessionEffectSupport.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionRuntimeSupport.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionMatchFlowSupport.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionCustomItemRuntime.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionFalloutRuntime.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionSpinjitzuRuntime.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionKarmaRuntime.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionProximityMineRuntime.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionTimeCapsuleRuntime.java`
  - Internal `GameSession` support/runtime split.
  - Used to keep match logic separated by concern without changing `GameSession` ownership.
  - Do not collapse these back into a single large class.

- `src/main/java/krispasi/omGames/bedwars/listener/BedwarsListener.java`
  - BedWars event plumbing.
  - Handles Bukkit events and delegates actual game rules into `GameSession`.

- `src/main/java/krispasi/omGames/bedwars/listener/BedwarsListenerCustomSupport.java`
- `src/main/java/krispasi/omGames/bedwars/listener/BedwarsListenerRuntimeSupport.java`
  - Internal `BedwarsListener` support split.
  - Keep listener code as event translation; do not move BedWars ownership out of `GameSession`.
  - Do not collapse these back into a single large class.

- `src/main/java/krispasi/omGames/bedwars/setup/BedwarsSetupManager.java`
  - `/bw setup` workflow.
  - Writes arena metadata back to `bedwars.yml`.

- `src/main/java/krispasi/omGames/bedwars/config/BedwarsConfigLoader.java`
  - Parser/validator for `bedwars.yml`.

- `src/main/java/krispasi/omGames/bedwars/shop/*`
  - Shop model, loader, quick-buy persistence, purchase behavior.

- `src/main/java/krispasi/omGames/bedwars/item/*`
  - Custom item model, loader, and item metadata.

- `src/main/java/krispasi/omGames/bedwars/timecapsule/*`
  - Time Capsule persistence, item payload metadata, and inventory serialization.
  - Owns the SQLite-backed pool used by the rotating Time Capsule item.

- `src/main/java/krispasi/omGames/bedwars/karma/*`
  - Permanent BedWars karma persistence.
  - Owns the SQLite-backed permanent karma pool used by the in-match karma runtime.

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
   - `.config-sync-version` (created on demand for BedWars version-based sync tracking)
   - `rotating-history.yml` (created on demand after first normal auto-rotation roll)
2. Construct `BedwarsManager`.
3. Load:
   - arenas
   - custom items
   - shop config
   - rotating history
   - rotating config merge
   - quick-buy DB
   - stats DB
   - time capsule DB
   - karma DB
   - skin selections
4. Start lobby and parkour leaderboards.
5. Construct `BedwarsSetupManager`.
6. Register `/bw`.
7. Register `BedwarsListener`.

BedWars config sync behavior on plugin version change:
- only `shop.yml`, `rotating-items.yml`, and `custom-items.yml` are regenerated from bundled defaults
- `bedwars.yml` is preserved
- sync version state is tracked in `plugins/OmGames/Bedwars/.config-sync-version`

#### 2.2.2 onDisable

- `BedwarsManager.shutdown()`:
  - stops active session
  - stops leaderboards
  - shuts down quick-buy DB
  - shuts down stats DB
  - shuts down time capsule DB
  - shuts down karma DB
  - clears dropped items in loaded arena worlds

### 2.3 Ownership Rules

- `BedwarsManager` owns:
  - arena catalog
  - active `GameSession`
  - `ShopConfig`
  - `CustomItemConfig`
  - `QuickBuyService`
  - `BedwarsStatsService`
  - `TimeCapsuleService`
  - `BedwarsKarmaService`
  - persistent BedWars skin selections
  - shared BedWars lobby world/spawn config
  - lobby/parkour leaderboards
  - temporary BedWars creator allowlist for setup access until restart

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
  - temporary karma and karma event runtime
  - internal support classes under `bedwars/game/` do not change this ownership; they are implementation detail

- `BedwarsListener` should stay as Bukkit event translation and call into `GameSession` for rules.
  - internal support classes under `bedwars/listener/` are still listener implementation detail, not a place to move match ownership

- `BedwarsLobbyParkour` owns lobby parkour runtime:
  - active run state
  - checkpoint progression
  - temporary hotbar control items
  - direction compass target updates
  - live parkour action-bar timer/status updates

### 2.4 Command Surface

Implemented in `BedwarsCommand`.

Public subcommands:
- `/bw stats [user]`
- `/bw quick_buy`
- `/bw skins`

Admin subcommands:
- `/bw start`
- `/bw test start`
- `/bw stop`
- `/bw tp <arena>|lobby`
- `/bw karma <user>`
- `/bw karma add <permanent|temporary> <user>`
- `/bw karma cause`
- `/bw creator add <user>`
- `/bw creator remove <user>`
- `/bw lobby parkour <start|checkpoint [x]|end>`
- `/bw lobby spawnMenuVillager <rotation>`
- `/bw game out [player]`
- `/bw game spectate [player]`
- `/bw game join <team|spectate> [player]`
- `/bw game revive <team>`
- `/bw game skipphase`
- `/bw reload`
- `/bw setup new <arena>`
- `/bw setup <arena> [key]`
- `/bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]`
- `/bw give <rotating-item>`
- `/bw time_capsule view <user> [time_id]`
- `/bw test_time_capsule view <user> [time_id]`

Permissions declared in `plugin.yml`:
- `omgames.bw.start`
- `omgames.bw.setup`
- `omgames.bw.reload`

Temporary creator notes:
- `/bw creator add <user>` and `/bw creator remove <user>` are OP-only management commands
- `/bw karma <user>`, `/bw karma add ...`, and `/bw karma cause` are also OP-only management commands
- `/bw give <rotating-item>` is self-target only
- in normal matches, only `krispasi_2` may use `/bw give <rotating-item>`
- in test matches, any OP player may use `/bw give <rotating-item>`
- `/bw give <rotating-item>` only works after the match countdown has started; it should not work during the pre-match lobby state
- `/bw time_capsule view <user> [time_id]` is also OP-only
- `/bw test_time_capsule view <user> [time_id]` is also OP-only
- temporary creators may use `/bw setup` and `/bw tp`
- temporary creators may also place/break blocks and use openable blocks in protected BedWars worlds when there is no active session in that world
- temporary creator access is in-memory only and is cleared on restart/shutdown

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
- `plugins/OmGames/Skins/`
- `plugins/OmGames/OmGames.db`

Files:
- `bedwars.yml`
- `shop.yml`
- `rotating-items.yml`
- `custom-items.yml`
- `rotating-history.yml`
- `../Skins/bedwars.yml`
- `../OmGames.db`

`rotating-history.yml`:
- stores persistent pick counters for normal-match auto-rotation balancing
- test matches ignore this history and do not increment it

### 2.7 SQLite

SQLite data currently lives in:
- `plugins/OmGames/OmGames.db`

#### 2.7.1 `OmGames.db -> quick_buy`

Table: `quick_buy`
- `player_uuid TEXT NOT NULL`
- `slot INTEGER NOT NULL`
- `item_id TEXT NOT NULL`
- PK: `(player_uuid, slot)`

Special marker:
- `__empty__` means intentional empty quick-buy slot.

#### 2.7.2 `OmGames.db -> bedwars_stats`

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

Derived display stats:
- `KDR`
  - derived from `kills / deaths`
  - if deaths are `0`, display it as the raw kill count ratio instead of storing a separate column
- `FKDR`
  - derived from `final_kills / final_deaths`
  - if final deaths are `0`, display it as the raw final kill count ratio instead of storing a separate column

#### 2.7.3 `OmGames.db -> time_capsules`

Table: `time_capsules`
- `capsule_id TEXT PRIMARY KEY`
- `queue_type TEXT NOT NULL`
- `created_by_player_uuid TEXT`
- `created_at INTEGER NOT NULL`
- `contents_base64 TEXT NOT NULL`

Queue split:
- `normal`
  - used by standard BedWars matches
- `test`
  - used by `/bw test start` sessions only

#### 2.7.4 `OmGames.db -> bedwars_karma`

Table: `bedwars_karma`
- `player_uuid TEXT PRIMARY KEY`
- `karma INTEGER NOT NULL`

Notes:
- this is permanent BedWars karma
- temporary karma is match-scoped runtime only and is cleared when the session ends/stops

### 2.8 Config Guide

#### 2.8.1 `bedwars.yml`

Root keys:
- `lobby`
- `leaderboard`
- `parkour-leaderboard`
- `match-events`
- `karma-events`
- `lobby-parkour`
- `arenas`

`lobby` fields:
- `world`
- `spawn`
- `menu-villager`

`lobby` runtime note:
- shared BedWars lobby defaults to world `bedwars_lobby`
- `/bw tp lobby`, match-end cleanup, arena quit/join safety snaps, ambient chime, lobby leaderboards, and lobby parkour should all resolve from this shared lobby config
- per-arena `game-lobby` is no longer part of the active schema

`leaderboard` world fallback:
- if no explicit world is set on `leaderboard`, use `lobby.world` first
- only then fall back to the generic BedWars leaderboard world resolution

`parkour-leaderboard` world fallback:
- if no explicit world is set on `parkour-leaderboard`, use `lobby-parkour.world` first
- if `lobby-parkour.world` is missing, use `lobby.world`
- only then fall back to the generic BedWars leaderboard world resolution

`match-events` fields:
- `enabled`
- `chance-percent`
- `events.<event-id>.weight`
- `moon-big.asteroids.fall-speed-blocks-per-second`
- `moon-big.asteroids.start-interval-min-seconds`
- `moon-big.asteroids.start-interval-max-seconds`
- `moon-big.asteroids.end-interval-min-seconds`
- `moon-big.asteroids.end-interval-max-seconds`
- `moon-big.asteroids.radius-min`
- `moon-big.asteroids.radius-max`
- `moon-big.asteroids.missing-block-chance`
- `moon-big.asteroids.crate-chance`
- `moon-big.asteroids.spawn-height-above-ground`
- `moon-big.asteroids.explosion-power-multiplier`

`karma-events` fields:
- `check-min-seconds`
- `check-max-seconds`
- `base-roll-chance-percent`
- `per-karma-chance-percent`

`karma-events` runtime note:
- each eligible participant gets a random scheduled karma check between `check-min-seconds` and `check-max-seconds`
- each check first rolls `base-roll-chance-percent`, then rolls `total karma * per-karma-chance-percent`, capped at `100%`

Supported event ids:
- `speedrun`
- `speedrun-any`
- `the-rapture`
- `benevolent-upgrades`
- `long-arms`
- `moon-big`
- `blood-moon`
- `sumo`
- `fallout`
- `chaos`
- `in-this-economy`
- `april-fools`

`april-fools` weighting note:
- if its configured weight is still the normal baseline `1`, treat it as effective weight `7` during April
- explicit disable via `0` should still stay disabled, and higher custom weights should stay as configured

`april-fools` runtime note:
- bridge egg should not launch a projectile; using it should instead pillar the user upward `30` blocks over time while building a vertical team-wool column under them
- fireball should seat its thrower on the launched projectile and manual dismount attempts should be blocked while that fireball exists
- the April Fools rotating-item override should auto-include both `bedrock` and `riding_fireball` when those rotating candidates exist

`speedrun-any` runtime note:
- all arena phase timers run `5x` faster (tier upgrades, bed destruction, sudden death, and game end)
- sudden-death world-border shrink duration also runs `5x` faster

Global world-border pacing note:
- tune sudden-death world-border pacing through arena `event-times` config (`sudden-death` and `game-end`) rather than code-side multipliers

`the-rapture` runtime note:
- schedules four hidden outcomes (one from each pair: `famine/meltdown`, `pestilence/pollution`, `war/conquest`, `death/eternity`) between match start and bed destruction with at least `60s` spacing
- each outcome plays a warning `10s` before trigger (`The anger of god is coming!`) with a wither ambient cue
- if item rewards target a respawning participant, drop those rewards at that player's base generator location

`moon-big` runtime note:
- use the gravity attribute for the low-gravity effect
- set player gravity to `0.01` from the vanilla default `0.08`
- do not use Feather Falling or Jump Boost as the event mechanic
- players who disconnect during the event must have gravity reset on quit, and rejoining participants during the same running match must receive the event gravity again
- moon-big asteroids should animate using temporary solid magma blocks instead of falling-block entities
- moon-big asteroids should spawn from `y=300`
- lock the world to nighttime and spawn falling asteroids that explode without block damage, leaving debris (basalt/deepslate/cobbled deepslate) and a rare loot crate barrel

`fallout` runtime note:
- once per second, pick one shared attribute from the Fallout pool and change it up or down for every active participant by its configured step amount
- Fallout uses one shared match-scoped value per attribute, so all participants always have the same current values and reconnecting players must receive those current values on rejoin
- Fallout should reset its tracked attributes to their normal defaults on death-to-spectator transitions, quit, world/session exit, and match stop

`sumo` runtime note:
- all active participants should have `Resistance V`
- all active participants should have their knockback-resistance attribute set to `5x` their default value

`in-this-economy` runtime note:
- `fireball`, `bed_bug`, and `dream_defender` stay purchasable at `4x` their normal price
- diamond and emerald map generators should drop gold instead, keeping their slower generator cadence
- each player kill should also reward the killer with `1` diamond and `1` emerald

`chaos` runtime note:
- all rotating items and rotating upgrades should be active for that match, regardless of the normal `2 items + 1 upgrade` auto-roll
- teams should begin with max base forge, and diamond/emerald map generators should start at tier III immediately

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
- `max-carry-amount`
- `limit.scope`
- `limit.amount`
- `enchants`
- `potion-effects`

Notes:
- `knockback-bonus` adds an `ATTACK_KNOCKBACK` item attribute modifier on the held weapon.
- `max-carry-amount` caps how many copies of that shop item a player may carry at once; purchases and dropped-item pickup should both respect it.
- `max-carry-amount` should also block moving extra copies from team chests or fake ender chests back into player inventory, while still allowing players to store extra copies inside those chests.
- Use config changes for shop balancing first.
- Shop UI border slots are reserved.
  - Avoid putting category entries on the outer top/bottom rows or the far left/right columns.
  - Quick Buy customization should only assign interior slots.

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
- match runtime always rolls `2` rotating items plus `1` rotating upgrade when candidates exist
- manual prestart rotation selection can choose any subset of rotating items and upgrades
- `/bw give <rotating-item>` may grant the caller any rotating item candidate from `shop.categories.rotating`, even if that item is not part of the current match rotation
- `woodoo_doll`
  - rotating held item that adds `10` temporary karma to the enemy player hit
  - should be consumed on a successful enemy hit
  - should use `max-carry-amount: 1`
- `broken_mirror`
  - rotating team upgrade with `4` levels
  - each purchased level adds `1` temporary karma to every current enemy participant
- `warden_family`
  - rotating shared upgrade with `3` levels
  - level 1 spawns `Gary the Warden` (500 HP) at a random emerald generator
  - level 2 adds `Gary's Wife` (200 HP), level 3 adds `Gary Jr.` (100 HP)
  - each level costs `2` diamonds
  - wife/junior deaths downgrade the shared level by `1`
  - if one falls to death, it digs away then re-emerges at a random emerald generator with the same health
  - wardens use a circular `30`-block territory around each diamond/emerald generator
  - if target leaves territory, the target is cleared and the warden digs away/re-emerges at a random emerald generator
- when `time_capsule` is active for a match, any available saved reward capsules from that queue should be granted to participants at match start before play begins
- if `shop.categories.rotating_upgrades` has no upgrade entries, rotating-upgrade selection falls back to upgrade entries found under `shop.categories.rotating`
- if `shop.categories.rotating_upgrades` does have entries, it is the authoritative upgrade/trap pool and runtime should not also consult legacy upgrade entries under `shop.categories.rotating`
- rotating trap entries also live under `rotating_upgrades`
  - keep `behavior: UPGRADE`
  - trap behavior and purchase rules still live in Java trap handling, not `TeamUpgradeType`

Merge behavior:
- `ShopConfig.merge(base, rotating)`
- rotating config item definitions with the same item id override base definitions from `shop.yml`
- rotating category entry slots remain additive; the rotating config should not replace existing base category slots

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
- `heal`
- `knockback`
- `lifetime-seconds`
- `health`
- `speed`
- `range`
- `uses`
- `cooldown-seconds`
- `save-chance-percent`
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
- `GIGANTIFY_GRENADE`
- `RAILGUN_BLAST`
- `PROXIMITY_MINE`
- `WOODOO_DOLL`
- `LOCKPICK`
- `TIME_CAPSULE`
- `UNSTABLE_TELEPORTATION_DEVICE`
- `MIRACLE_OF_THE_STARS`
- `SPINJITZU`
- `TOWER_CHEST`
- `STEEL_SHELL`
- `SHOCK_CELL`

Behavior notes:
- `FLAMETHROWER`
  - cone attack in front of the player
  - uses particles for the area preview and directly damages/ignites targets in the cone
- `TACTICAL_NUKE`
  - its temporary countdown block override should skip existing `RED_CONCRETE` blocks entirely
- `ABYSSAL_RIFT`
  - fixed deployable aura
  - `abyssal_rift` / `Abyssal Rift: Domination` uses model `om:rift1` and buffs allies while weakening enemies in the radius
  - `abyssal_rift_regeneration` / `Abyssal Rift: Regeneration` uses model `om:rift2` and heals allied players in the same radius
  - `abyssal_rift_regeneration.heal` controls the direct heal amount per aura tick; the aura ticks once per second
  - `abyssal_rift_corruption` / `Abyssal Rift: Corruption` uses model `om:rift3` and damages enemy players in the same radius
  - `abyssal_rift_corruption.damage` controls the direct damage amount per aura tick; the aura ticks once per second
  - has separate hitbox/display/nameplate entities
  - health/range come from config
- `CRYSTAL`
  - direct crystal contact damage defaults to `1` in all modes
  - crystal contact damage should hit both allies and enemies; same-team crystal contact should only be cancelled if the resolved contact damage is `0`
- `RESPAWN_BEACON`
  - solo teams should auto-activate it on death from inventory and use the normal `5s` respawn delay/title
  - manual use on teams with living teammates should keep the configured beacon delay and revive all currently eliminated online teammates still in that team
- `ELYTRA_STRIKE`
  - purchased as a held item
  - right-click activation equips temporary Elytra, teleports above team spawn, launches directly into glide, and cleans up on landing/death/quit/session end
  - while active it should be glide-only; do not leave normal creative-flight toggling enabled
- `GIGANTIFY_GRENADE`
  - thrown as a gravity-free snowball projectile with custom projectile metadata
  - only affects enemy players on direct hit; block hits should only despawn the projectile
  - scales the target up over 2 seconds, holds for 3 seconds, then shrinks over 3 seconds
  - effect cleanup must restore the player's BedWars scale on death, quit, world/session exit, and natural expiry
- `RAILGUN_BLAST`
  - purchased as a held rotating item and activated on right-click
  - spends 5 seconds charging with a visible straight-line preview on the initial locked aim; once charging starts, the owner should not be able to move or turn until the shot resolves or is cancelled
  - uses a 75-block max range unless a shorter in-bounds line is forced by the arena corner bounds
  - charge and fire sounds should be heard along the beam line, not only at the caster origin
  - the fired beam should stay inside the arena corner bounds, render as roughly a 5-block-thick cylinder, and instantly kill enemy players while still recording normal BedWars combat credit
- `PROXIMITY_MINE`
  - bought as a normal placeable block item and placed as a `STONE_PRESSURE_PLATE`
  - should spend 5 seconds priming after placement; while priming it shows a shared floating progress bar above the mine
  - once armed, it should trigger when an enemy player steps directly onto the mine block, using only the lowest `0.8` blocks of vertical trigger height above the mine, and detonate through the normal TNT explosion path
  - `custom-items.yml -> proximity_mine.damage` overrides the mine's direct player damage as the exact dealt hit; non-positive values keep the default scaled TNT damage path
  - should use placed-block tracking so it can be broken, dropped, rolled back, and chain-exploded like other BedWars placed blocks
- `WOODOO_DOLL`
  - rotating held item used as a melee curse item
  - hitting an enemy player adds `10` temporary karma to that victim
  - the held item should be consumed immediately after the successful enemy hit
- `LOCKPICK`
  - rotating held item used on enemy team storage inside that base's radius
  - right-clicking a normal chest/trapped chest starts a 10-second countdown above the chest, then grants that player 60 seconds of access to that base team's normal chests
  - starting a normal-chest lockpick should show a big title to all online players on that team saying `<robber> is robbing your team chest`
  - right-clicking an enemy ender chest opens a team-member target GUI unless the player already has active lockpicked access for that base team
  - starting an ender-chest lockpick should show a big title only to the selected target saying `<robber> is robbing your ender chest`
  - lockpick countdowns and the 60-second access windows should show a timer above the clicked chest that is only visible to the player who triggered that lockpick
  - ender chest target selection starts a 20-second countdown above the chest, then grants 60 seconds where right-clicking that base team's ender chests opens the selected player's fake BedWars ender chest until the timer expires
  - once the ender-chest access timer expires, those enemy ender chests should revert to opening the viewer's own fake ender chest again
- `TIME_CAPSULE`
  - rotating held item bought as an `ENDER_CHEST`
  - right-clicking the bought item opens a 27-slot pack GUI similar to the fake ender chest, consumes that item, and rolls each filled slot independently against `save-chance-percent`
  - only the winning slots are serialized into SQLite; failed slots are intentionally lost
  - saved capsules are split into separate `normal` and `test` queues based on whether the source match came from `/bw start` or `/bw test start`
  - when Time Capsule is active in a later match, participants should receive claimed reward capsules from that same queue at match start
  - claimed reward capsules should identify which player packed them when that creator is known
  - `/bw time_capsule view <user>` should list only the creator's currently stored normal-queue capsules, using ids in `MM_dd_HH_mm_ss` format
  - `/bw time_capsule view <user> <time_id>` should open a read-only view of that current normal-queue capsule
  - `/bw test_time_capsule view <user>` should list only the creator's currently stored test-queue capsules, using ids in `MM_dd_HH_mm_ss` format
  - `/bw test_time_capsule view <user> <time_id>` should open a read-only view of that current test-queue capsule
  - if the queue has fewer saved capsules than participants but at least one exists, claimed rewards may duplicate so every participant still receives one
  - each claimed source capsule is deleted from SQLite immediately after that match claims it
- `BRIDGE_BUILDER`
  - right-clicking a block places a piston anchor at the clicked placement position
  - the tunnel should extend from that piston anchor in the player's horizontal facing direction, not from the player's feet
- `HAPPY_GHAST`
  - should take normal damage from players and projectiles, including same-team hits, and use attribute-based knockback resistance; do not add ghast-specific damage or knockback handling in the listener
  - custom `speed` should be applied as a multiplier on the native Happy Ghast move/flying speed so config values below `1.0` reliably slow the mount down
  - summon nameplate should show current health above it alongside the despawn timer
- `UNSTABLE_TELEPORTATION_DEVICE`
  - purchased as a held item
  - right-click activation rolls one teleport outcome
  - random-location outcome must land on a safe block with space above it
  - every destination must also keep the player feet/head inside the arena corner bounds; do not trust `getHighestBlockAt` above the configured map ceiling
  - supports `cooldown-seconds` in `custom-items.yml`
- `MIRACLE_OF_THE_STARS`
  - purchased as a held item
  - right-click activation recalls alive online teammates to base after 5 seconds
  - must fail once sudden death is active and cancel if sudden death begins during the windup
- `SPINJITZU`
  - purchased as a held rotating item using a `BLAZE_POWDER` icon
  - right-click activation grants 10 seconds of spinjitzu, making the user immune to damage while active
  - should raise the user's native `STEP_HEIGHT` so 2-block climbs stay smooth instead of faking the climb with teleports
  - should add a small movement-speed bonus, keep the user hovering about `0.5` blocks above nearby ground, and render a roughly 3-block-tall fiery tornado around them
  - enemy players inside the configured `range` should take `damage` once per second while touching that tornado
  - cleanup must restore the player's previous movement speed and step height on death, quit, world/session exit, and natural expiry
- `TOWER_CHEST`
  - chest deployable that builds a fixed wool tower aligned to player facing
  - uses team wool plus placed ladders, follows the fixed 7-layer popup-tower layout in `GameSession.TOWER_CHEST_LAYERS`, only fills air blocks inside the map, ignores anti-build placement restrictions, and removes the center chest shortly after placement
- `STEEL_SHELL`
  - purchased as a held item using a `NETHERITE_BLOCK` icon
  - right-click activation builds a temporary bedrock prison around the user for 10 seconds if every shell block fits in air inside the map
  - while active it applies `Resistance V` and then restores any previous resistance effect when the shell expires
- `SHOCK_CELL`
  - rotating held item with model `om:coiled_energy`
  - right-click charges the held item by `10`, up to `100`, and uses the durability bar to display charge
  - shift + right-click fires and resets charge to `0`
  - charge `10-49` fires a straight shock bolt with range equal to charge and damage `10 + charge * 0.2`
  - charge `50+` fires a spherical shockwave with radius `charge / 3`, same damage formula, and only breaks tracked BedWars placed blocks

### 2.9 Match Event Workflow

Prestart event control lives in the team-assign menu:
- left-click `Game Events` to enable or disable events for that match
- right-click `Game Events` to force a specific event or switch back to `Auto Random`

Forced event selection is prestart-only state and should not be conflated with the runtime `activeMatchEvent`.

### 2.10 Setup Workflow

Manager: `BedwarsSetupManager`

Create arena:
- `/bw setup new <arenaId>`
  - seeds `map-lobby` to the executor's current location by default

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
- `map-lobby`
- `<team>.bed`
- `<team>.spawn`
- `<team>.base-gen`
- `<team>.shop`
- `<team>.upgrades`
- `generator.diamond.<n>`
- `generator.emerald.<n>`

Setup key autocomplete:
- tab completion should prefer canonical team-first keys such as `red.bed`, `red.spawn`, `red.shop`, `red.upgrades`, and `red.base-gen`
- legacy alias forms may still be accepted by parsing, but should not be suggested in autocomplete

Team setup completion feedback:
- when a team gets its last missing setup field (`bed`, `spawn`, `base-gen`, `shop`, `upgrades`), `/bw setup` should send `Team <color> setup complete`
- when the last missing diamond generator is filled, `/bw setup` should send `Diamond generators setup complete`
- when the last missing emerald generator is filled, `/bw setup` should send `Emerald generators setup complete`

### 2.11 Placement Rules

Put changes here:
- match rules and lifecycle -> `GameSession`
- event plumbing -> `BedwarsListener`
- setup command behavior -> `BedwarsSetupManager`
- `bedwars.yml` parsing -> `BedwarsConfigLoader`
- shop parsing/model -> `shop/*`
- custom item parsing/model -> `item/*`

Do not push BedWars rules into `OmGames`.
Do not re-introduce large BedWars god classes; use the existing support/runtime split pattern when adding substantial new logic.

### 2.12 High-Risk Invariants

- Only one active `GameSession` should exist at a time.
- `stop()` cleanup must remove tasks, entities, sidebars, displays, and match-only state.
- Team lookup is case-insensitive through `TeamColor.fromKey`, but config keys should stay canonical lowercase.
- Team config keys should use canonical `aqua`; legacy `cyan` should remain accepted where `TeamColor.fromKey` already handles aliases.
- Keep BedWars config parsing tolerant of legacy casing and aliases where the loaders already support them.
- Shop + rotating config are both active after reload because of merge behavior.
- If stats are disabled for a session, progression/wins should not be awarded.
- Outside a running BedWars match, protected BedWars worlds should still block casual terrain changes like farmland trampling unless the player is an allowed editor.
- Outside a running BedWars match, players should not be able to rotate, take from, or break item frames in protected BedWars worlds unless they are allowed editors.
- During a running BedWars match, normal chests/trapped chests inside a team's base radius are locked to that team until that team's bed is destroyed; afterward they are open to everyone.
- If a pending respawn later turns into a true elimination because respawns are no longer allowed, final-death and final-kill stats should still resolve from that original death.
- If a running participant quits while their bed is still alive, or while they still have respawn grace from a pre-bed-break death, keep their team assignment so they can rejoin that running match; otherwise remove them immediately, and if that was the last remaining player on their team, normal team-elimination and win-resolution must still happen from that quit.
- If a participant or spectator quits from the active arena world, move them to the shared BedWars `lobby.spawn` before logout so reconnecting does not leave them stranded on the map.
- If a player joins while outside the running match but still inside a BedWars arena world, non-editor players should be snapped back to the shared BedWars `lobby.spawn` first as a safety net.
- Party EXP should only be paid to players who are still active participants when the match finishes or who disconnect and later rejoin before rewards are flushed; players who leave and do not finish the match must not receive party EXP.
- `netherite_spear` movement boost reuse must be hard-blocked for 5 seconds with native `NETHERITE_SPEAR` cooldown plus short follow-up velocity suppression on denied attempts; do not rely on message-only listener gating.
- Lobby-mode prestart should build a temporary 15x15 barrier platform centered under the resolved `map-lobby` location and restore the original blocks when the session leaves lobby/starts the match.
- Starting a BedWars lobby from the team-assign menu should only teleport players who are currently assigned in that prestart session; unassigned players shown in the menu must stay where they are.
- The team-assign start menu should list online players from the selected arena world and from the shared BedWars lobby world so players waiting in `bedwars_lobby` can be assigned before start.
- Match end cleanup should return all remaining arena spectators to the shared BedWars `lobby.spawn`; `map-lobby` is for prestart/spectate flows, not post-match cleanup.
- `/bw game spectate` can only be run by a player already standing in the active BedWars world.
- When there is no active BedWars session, the shared BedWars lobby world should play a `BLOCK_AMETHYST_BLOCK_CHIME` ambient sound at `0 90 0` for players in that world at random intervals between 30 and 60 seconds.
- Players put into spectator mode by `/bw game spectate` are locked to the active BedWars world until `/bw game out` or session end.
- BedWars full-screen titles should use one shared timing window with fade-in and fade-out instead of per-feature custom lengths.
- Active lobby parkour runs should keep the current timer in the action bar; the redstone reset control should instantly restart the run, the last-checkpoint control should instantly restart the run until a real checkpoint has been reached, and only the exit control should apply the temporary pressure-plate lock.

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

If the entry is a rotating team upgrade:
- prefer `shop.categories.rotating_upgrades.entries`
- auto rotation expects `1` rotating upgrade per match alongside `2` rotating items
- manual rotation can override that mix and select any subset of rotating entries
- rotating traps use the same pool, but still render and purchase through the trap queue in `UpgradeShopMenu`

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

## 3) Egg Hunt

### 3.1 Top-Level Layout

- `src/main/java/krispasi/omGames/egghunt/*`
  - Temporary Egg Hunt event implementation.
  - Owns `/egghunt`, persistent egg point storage, runtime timer/countdown, item displays, and sidebar scoreboard.

### 3.2 Command Surface

Egg Hunt admin subcommands:
- `/egghunt add`
- `/egghunt prepare`
- `/egghunt timer <seconds>`
- `/egghunt start`
- `/egghunt clear <near/all/scoreboard>`

Egg Hunt clear behavior:
- `clear near` permanently removes saved egg points within the near-clear radius of the executing player
- `clear all` permanently removes all saved egg points
- if an Egg Hunt session is active, cleared points should also disappear from the live session immediately
- `clear scoreboard` removes the Egg Hunt sidebar scoreboard without deleting saved egg points

### 3.3 Runtime Data Layout

Egg Hunt runtime files live in:
- `plugins/OmGames/EggHunt/`

Files:
- `egghunt.yml`

`egghunt.yml` keys:
- `timer-seconds`
- `points`

## 4) Chess

### 4.1 Top-Level Layout

- `src/main/java/krispasi/omGames/chess/*`
  - Chess game implementation.
  - Owns `/chess`, saved boards, active match runtimes, item displays, interaction boxes, move validation, undo/redo state, timers, and SQLite match/stat logging.
  - `ChessManager` is the command/event coordinator.
  - `ChessMatchRuntime` owns one active match on one board timestamp.
  - Keep Chess logic inside this package; do not push Chess rules into BedWars or Egg Hunt classes.

### 4.2 Command Surface

Operator subcommands:
- `/chess board build <x> <y> <z>`
- `/chess board blocks <b1> <b2> <b3>`
- `/chess board blocks reset`
- `/chess board reset`
- `/chess board remove <timestamp|*>`
- `/chess match white <player> [player] [player]`
- `/chess match black <player> [player] [player]`
- `/chess match start [board_timestamp]`
- `/chess match test`
- `/chess match cancel <timestamp|*>`
- `/chess match settings do_movement_check <true|false>`
- `/chess match settings visualize_movement_check <true|false>`
- `/chess match settings do_endgame_checks <true|false>`
- `/chess match settings allow_undo <true|false>`
- `/chess match settings show_annotation <true|false>`
- `/chess match settings figure_style <default|flat>`
- `/chess log print [timestamp|*]`
- `/chess log delete <timestamp|*>`
- `/chess log search <player> [player...]`
- `/chess timer off`
- `/chess timer time <duration> [check <duration>]`

Team/player subcommands:
- `/chess resign`
- `/chess draw`
- `/chess undo`
- `/chess redo`
- `/chess rewind`
- `/chess forward`
- `/chess checkmate`

### 4.3 Runtime Data Layout

Chess uses:
- item displays for visible pieces
- interaction entities for piece hitboxes and board-square click targets
- `plugins/OmGames/OmGames.db`

SQLite tables:
- `chess_matches`
- `chess_match_events`
- `chess_player_stats`
- `chess_boards`
- `chess_active_match_state`

### 4.4 Runtime Notes

- The board is built in `minecraft:bedwars_lobby`.
- The configured board corner is treated as the white A1 side; files run A-H across positive X and ranks run 1-8 toward negative Z.
- Board tiles are 2x2 blocks, with light squares using palette block `b1`, dark squares using `b2`, and movement highlights using `b3`.
- Defaults are `minecraft:smooth_quartz`, `minecraft:coal_block`, and `minecraft:smooth_basalt`.
- A1 is white's left rook square and H8 is black's left rook square.
- Piece item displays use `minecraft:iron_nugget` with `ItemMeta#setItemModel()`.
- Normal models are `om:<piece>` for white and `om:black_<piece>` for black; selected models are `om:selected_<piece>`.
- Multiple boards and active matches may exist at the same time; each active match is identified by its match timestamp and runs on one board timestamp.
- `/chess match start` without a board timestamp uses the most recent saved board.
- A player may be assigned to any side in any number of concurrent matches; clicked board entities route moves to the match for that entity timestamp.
- Each active match board owns 64 square interaction boxes, 32 piece interaction boxes, and 32 item displays.
- Chess board entities are persistent and can be removed with `/chess board remove <timestamp|*>`.
- Chess interaction entities use persistent data and scoreboard tags for identity; do not rely on visible custom names.
- Active non-test matches are saved in `chess_active_match_state` so they can continue after restart until a win, draw, resign, cancel, board reset, or board removal.
- During an active match, online team players in the board world are put in Adventure mode with flight enabled and 16-block block/entity interaction reach; this must be restored when they leave the board world or the match ends.
- Flat figure style uses `om:<side>_<piece>_icon` item models on `minecraft:iron_nugget`; default style keeps the existing standing figure models.
- Pawn promotion uses a forced small inventory selection for bishop, horse, queen, or rook, not captured-piece selection.
- Chess timer durations accept decimal values with optional `s`, `m`, `h`, or `d` units; unqualified match time defaults to minutes and unqualified check bonus defaults to seconds.

## 5) Bank

### 5.1 Top-Level Layout

- `src/main/java/krispasi/omGames/bank/fortuna/*`
  - Fortuna betting implementation under the Bank root command area.
  - Keep Fortuna logic inside this package; do not push Fortuna rules into BedWars, Egg Hunt, or Chess classes.
  - Only use `OmGames` for plugin lifecycle wiring, command registration, and listener registration.

### 5.2 Runtime Data Layout

Fortuna runtime files live in:
- `plugins/OmGames/Bank/Fortuna/`

Bank may use shared plugin storage only when the schema is explicitly defined:
- `plugins/OmGames/OmGames.db`

Files:
- `fortuna.yml`

`fortuna.yml` keys:
- `map-display.width`
- `map-display.height`
- `map-display.map-ids`
- `next-match-id`
- `matches`

### 5.3 Command Surface

Implemented in `FortunaCommand`.

Operator subcommands:
- `/bank fortuna`

Permissions declared in `plugin.yml`:
- `omgames.fortuna.manage`

### 5.4 Ownership Rules

- Fortuna should own its own commands, listeners, services, config loading, and persistence helpers.
- Use lowercase Java package names, even though the runtime folder is `Bank`.
- Keep Fortuna changes isolated from existing BedWars, Egg Hunt, and Chess behavior unless integration is explicitly requested.

### 5.5 Fortuna Display Notes

- Fortuna display uses a fixed 3x2 map board.
- Default map ids are `1459`, `1460`, `1461`, `1462`, `1463`, and `1464`.
- `/bank fortuna` opens the operator GUI for creating matches, changing live odds, activating matches, ending active matches as home win, draw, or away win, cleaning the display board, and deleting saved matches.
- Text entry for match names, dates, times, and odds is collected through chat prompts started from the GUI.
- The map display renders an active match first; when an active match is finished, that finished match remains pinned as the result strip while the main display area shows the next upcoming match when one exists, or the finished match itself when it does not.
- The Clean Board GUI action clears the current display render and unpins any finished result; it does not delete saved matches.
- Match deletion is available from the match detail GUI and should remain a deliberate action, not an accidental one-click removal.

## 6) Random

### 6.1 Top-Level Layout

- `src/main/java/krispasi/omGames/random/*`
  - Miscellaneous OmGames utility features that do not belong to BedWars, Egg Hunt, Chess, or Bank.
  - Keep Random features isolated from game-mode ownership unless integration is explicitly requested.

### 6.2 GIF Map Player

- Runtime owner: `RandomGifManager`
- Command owner: `RandomCommand`
- Listener owner: `RandomListener`

Command surface:
- `/omgames gif`
  - OP/default-permission GUI for managing GIF-to-map links.
  - Opens the GIF manager menu.

Permissions declared in `plugin.yml`:
- `omgames.random.gif`

Runtime data layout:
- `plugins/OmGames/Random/gifs/`
  - Server folder where `.gif` files are dropped manually.
- `plugins/OmGames/Random/gifs.yml`
  - Stores persisted GIF filename to map-id links.

Behavior notes:
- The GUI flow is: create new GIF -> choose a `.gif` file from `plugins/OmGames/Random/gifs/` -> choose a map board size -> enter the first target map id in chat.
- Supported board sizes are selected in the GUI and range up to `4x4`.
- Multi-map GIF boards use consecutive map ids from the entered first map id, laid out left-to-right and top-to-bottom.
- GIF animation only advances when at least one linked `FILLED_MAP` from that GIF board is placed in an item frame and at least one player is within `20` blocks of that item frame.
- When no player is near a placed linked map, the GIF runtime resets the renderer state to frame `0` and does not send animation updates.
- Existing map ids must already exist on the server before linking; the GIF manager does not create new vanilla maps.

## 7) Halls of Carnage

### 7.1 Top-Level Layout

- `src/main/java/krispasi/omGames/hallsofcarnage/*`
  - Initial Halls of Carnage implementation.
  - Owns `/hoc`, Halls config/resource loading, lobby/menu-villager handling, scenario discovery, and Halls shame persistence.
  - Keep Halls logic isolated from BedWars, Egg Hunt, Chess, Bank, and Random classes.
  - `HallsSession` owns active session state; `HallsSessionTrapRuntime` is its session-owned trap placement/ticking helper.

### 7.2 Command Surface

Public subcommands:
- `/hoc menu`
- `/hoc scenarios`
- `/hoc sessions`
- `/hoc top`
- `/hoc shame [player]`
- `/hoc tp`

Operator subcommands:
- `/hoc start <scenario> [player...]`
- `/hoc stop <session_id|*>`
- `/hoc floor <session_id> <floor>`
- `/hoc scenario <scenario>`
- `/hoc give <item> [amount]`
- `/hoc reset confirm`
- `/hoc shame set <player> <amount>`
- `/hoc shame add <player> <amount>`
- `/hoc lobby setspawn`
- `/hoc lobby spawnMenuVillager [rotation]`
- `/hoc reload`

Permission declared in `plugin.yml`:
- `omgames.hoc.manage`

### 7.3 Runtime Data Layout

Halls runtime files live in:
- `plugins/OmGames/HallsOfCarnage/`
- `plugins/OmGames/OmGames.db`

Files:
- `halls-of-carnage.yml`
- `scenarios/*.txt|*.yml|*.yaml`
- `level/**`
- `level_type/**`
- `modifiers/**`
- `breakables/*.txt|*.yml|*.yaml`
- `traps/*.txt|*.yml|*.yaml`
- `items/**/*.txt|*.yml|*.yaml`

SQLite tables:
- `hoc_shame`
- `hoc_completed_scenarios`

### 7.4 Runtime Notes

- Halls uses the `om:halls_of_carnage` dimension configured by OmVeins, but must not call `OmVeinsAPI` during startup.
- The human-built lobby is centered near `0 70 0`; automated session/dungeon placement must stay at least 1000 blocks away.
- Players in the Halls world are kept in Adventure mode, with full hunger and natural regeneration disabled.
- Shame leaderboards are ascending because lower shame is better.
- `/hoc start <scenario> [player...]` allocates a session origin, builds the first start floor/elevator shell, teleports players into it, and tracks changed blocks for cleanup.
- `/hoc stop <session_id|*>` restores changed blocks and returns online players in that Halls world to the configured lobby spawn.
- `/hoc floor <session_id> <floor>` is an OP-only development shortcut for rebuilding an active placeholder floor while preserving elevator transfer chest contents.
- `/hoc scenario <scenario>` is an OP-only debug command that prints the loaded parsed scenario data and the YAML view copied from the active server data folder.
- `/hoc reset confirm` is an OP-only development command that deletes and recopies game resource folders (`scenarios`, `level`, `level_type`, `modifiers`, `breakables`, `items`) from bundled defaults while preserving lobby config in `halls-of-carnage.yml`; active sessions must be stopped first.
- `HallsExplorationGenerator` owns deterministic-per-session exploration layout planning.
- Halls level types are loaded from `plugins/OmGames/HallsOfCarnage/level_type/*.txt|*.yml|*.yaml`.
- Level type fields currently parsed are `id`, `name`, `corridor-generation`, `materials.*`, `wall-palettes`, and `pillar-palettes`; monster/modifier sections may exist in resource files for future systems.
- Current exploration floors bake layered room, corridor, shell, and walkable masks in memory before rendering; Java then places room shells, corridor openings, lights, props, and normal corridors around interior-only `level/<level_type>/exploration_*.txt` room masks.
- Halls scenario floor ranges are parsed into runtime floor definitions; exploration generation uses the active floor's configured `rooms` count and spreads breakable props from the configured `breakables` count.
- If a scenario floor is not explicitly configured but a prior exploration floor is configured, runtime reuses that prior exploration floor definition for the requested floor instead of falling back to the generic 8-room placeholder.
- Exploration floors grow their per-session generation/cleanup radius from the configured room count and retry with larger radii if planning underfills.
- Exploration corridor routing uses turn-aware cardinal pathing over the baked mask, grows primarily through room-to-room corridor clusters instead of connecting every room into one shared corridor spine, rejects short zigzag paths that read as diagonal, adds direct room-to-room loop corridors and side branches after the main connected layout is built, and checks reachability from the elevator before the plan is accepted.
- The first exploration room connected to the elevator is given extra onward room-to-room exits when enough rooms exist, so the elevator does not feed into a single-path start.
- Exploration corridor rendering builds a complete shell around the planned path before carving walkable cells so bends keep walls.
- Generated corridors use ceiling-embedded light blocks so the walkable corridor remains 3 blocks tall, and the elevator has a ceiling light.
- The elevator exterior vestibule is generated as a sealed mini-tunnel outside the door; opening the door clears only the passage while preserving the vestibule floor, side walls, and ceiling.
- Halls physics item displays use a 1-tick interpolation delay and short teleport duration for smoother falling/pickup visuals.
- Halls floor loot/drop placeholders should use session-owned physics drops (`ItemDisplay` plus `Interaction`) instead of vanilla dropped item entities; players pick them up by right-clicking with an empty hand.
- Halls physics item displays are fixed, flat item displays with randomized yaw so dropped items read as lying on the floor instead of upright.
- Halls physics item displays and breakable prop block displays use tiny random per-axis scale jitter to reduce display z-fighting.
- Halls breakable props are session-owned display/interactions and may be multi-part prop archetypes such as barrels, chests, tables, chairs, stools, radiators, and metal barrels; keep cleanup routed through `HallsSession`.
- Halls breakable prop archetypes are loaded from `plugins/OmGames/HallsOfCarnage/breakables/` and seeded from bundled defaults.
- Breakable files define `id`, `break-message`, `hitbox-height`, `particle-material`, `parts`, and weighted `loot` entries.
- Supported placeholder breakable loot keywords are `wood_scrap`, `iron_scrap`, `diamond_scrap`, `redstone_scrap`, `random_scrap`/`scrap`, `blueprint`/`normal_blueprint`/`rare_blueprint`, and `coin`/`coins`.
- Halls item definitions are loaded recursively from `plugins/OmGames/HallsOfCarnage/items/` and seeded from bundled defaults grouped into category folders.
- Item files define `id`, `name`, `category`, `rarity`, `material`, optional `item-model`, optional `armor-model`, `max-stack-size`, `lore`, an unused-for-now `recipe` scrap cost map, and an optional `stats` map.
- Armor `item-model` controls the item icon/model; armor `armor-model` is written to Paper's equippable component for the worn armor model.
- Blueprint item files should not define `recipe`; future building and camp systems should own blueprint/building costs separately from blueprint item metadata.
- Item recipes are parsed for future crafting stations but should not be rendered directly on item lore.
- Item `stats` values are written into item PDC as `hoc_stat_<stat_id>` and rendered into item lore for test visibility.
- Scenario `allowed-items` is parsed by category, and `blueprint-pools.normal` / `blueprint-pools.rare` control blueprint keyword drops.
- Blueprint defaults currently cover every GDD building family: cooking pot, weapon bench, armory, grindstone, storage lockers by size, mycelia farm, elevator drill, scanner, bounty board, and sculk purifiers by size.
- Breakable loot may reference concrete item ids or category keywords such as `weapon`, `armor`, `ranged`, `utility`, `rare_weapon`, `rare_armor`, `rare_ranged`, and `rare_utility`.
- The generic `blueprint` loot keyword rolls the scenario normal blueprint pool with a small rare-pool chance; `normal_blueprint` and `rare_blueprint` force those pools.
- `/hoc give <item> [amount]` is an OP-only self-target test command for giving loaded Halls item definitions.
- Halls armor items equip into empty matching armor slots from `/hoc give` and from right-click physics-drop pickup before falling back to hotbar insertion.
- Halls coin drops use session-owned physics drops but bypass normal inventory pickup; right-clicking the coin adds it directly to the shared session coin counter even when the hotbar is full.
- Halls physics drops settle once they land on a support surface and stop ticking until a nearby breakable prop is destroyed or a new drop is spawned.
- Halls physics drops can land on top of current breakable props as temporary support surfaces; if that prop breaks, nearby settled drops are woken and resume falling.
- Placeholder Halls scrap items are split into single-item drops and use max stack size `1` so they do not stack in player inventories.
- Elevator scrap deposit consumes only the currently selected hotbar stack, not every scrap item in the player hotbar/offhand.
- Halls room mask files use `O` for open interior and `X` for internal blocked cells only; do not define outer walls, lights, or prop locations in those room files.
- `HallsLayoutLoader` tolerates old copied room files by stripping a full `X` perimeter and treating non-`X` marker characters as open cells; this is runtime parsing tolerance, not file migration.
- If every participant in a session disconnects, the session is stopped after `sessions.disconnect-grace-seconds`.
- Current Halls implementation is still early; full dungeon generation, real floor progression, polished elevator transitions, ghost state, full item definitions, scrap storage, camps, polished trap visuals/config, sculk, and monsters are pending.
- Exploration doorway selection must reject side offsets where the room mask has `X` at the edge or first inward cell.
- Howling Corridors room resources are seeded from all bundled `exploration_*.txt` templates listed in `HallsOfCarnageManager`.
- Frozen Halls and Deep Crypt room resources are also seeded from their bundled `exploration_*.txt` templates listed in `HallsOfCarnageManager`; use `/hoc reset confirm` to copy newly bundled resource files into an existing server data folder.
- Exploration floors have first-pass session-owned trap generation/runtime for holes, bridged holes, bear traps, proximity mines, swinging blades, wall spikes, Frozen Halls falling ice, and Deep Crypt poison darts.
- Trap placement uses the generated walkable mask and BFS reachability before accepting an unbridged pit; pits that would disconnect traversal receive a spruce bridge.
- Halls trap animation/cooldown logic must use `HallsSessionTrapRuntime`'s session-local scheduler tick, not world time, because the Halls dimension may have frozen or nonstandard time progression.
- Halls trap archetypes are loaded from `plugins/OmGames/HallsOfCarnage/traps/` and seeded from bundled defaults.
- Trap files define `id`, `kind`, `weight`, optional `level-types`, `block-material`, optional `model-material`, optional `item-model`, `model-scale`, timing, damage/radius, explosion power, and hole size/depth.
- Exploration floor trap counts come from scenario floor field `traps`; hole/pit generation is controlled separately by scenario floor field `holes`.
- Exploration floor layout templates are loaded with runtime rotations so repeated room files can appear in different orientations.
- Exploration floor generation uses a fresh random seed per floor rebuild/session attempt instead of replaying the same layout from scenario and floor id.
- Trap placement reserves occupied cells before breakable placement; breakables should not spawn on trap footprints.
- Hole traps carve a rectangular configurable 5x5-15x15 room-interior mask that may intersect internal blocked room cells/pillars, build pit walls, avoid doorway zones, and clear down to the current maximum pit depth during floor rebuild cleanup.
- Hole trap masks are rejected if any pit cell is near an already placed trap, so later full-mask holes should not overlap bear traps/mines/blades.
- Proximity mines trigger in a larger radius and reserve/validate a 3x3 obstacle footprint for traversal.
- Swinging blade traps use a stretched ceiling `BlockDisplay` rail plus a moving vanilla iron-sword `ItemDisplay` blade by default.
- Wall spikes and poison darts mount from adjacent room walls as display-only fixtures instead of solid blocks, and wall-trap candidates should stay away from room entrances.
- Wall spikes animate a sword display inward from the wall and check a forward lane up to their configured radius, defaulting to 3 blocks and stopping at walls.
- Falling ice traps may use display-only ceiling fixtures when configured, spawn temporary falling block-display shards around the trap cell, and must not place solid trap blocks; `ceiling-material: AIR` keeps the trap position hidden.
- Poison darts trigger only when a player crosses the forward lane, defaulting to 5 blocks with a 3-second cooldown.
