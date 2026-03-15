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
- Do not create or grow monolithic classes.
- If a class is approaching roughly `2000` lines, split it by responsibility before adding more code.
- Prefer extracting support/runtime/helper classes by concern while preserving ownership boundaries and cleanup invariants.
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

- `src/main/java/krispasi/omGames/bedwars/game/GameSessionEffectSupport.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionRuntimeSupport.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionMatchFlowSupport.java`
- `src/main/java/krispasi/omGames/bedwars/game/GameSessionCustomItemRuntime.java`
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

Admin subcommands:
- `/bw start`
- `/bw test start`
- `/bw stop`
- `/bw tp <arena>|lobby`
- `/bw creator add <user>`
- `/bw creator remove <user>`
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

Temporary creator notes:
- `/bw creator add <user>` and `/bw creator remove <user>` are OP-only management commands
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
- `plugins/OmGames/OmGames.db`

Files:
- `bedwars.yml`
- `shop.yml`
- `rotating-items.yml`
- `custom-items.yml`
- `../OmGames.db`

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

### 2.8 Config Guide

#### 2.8.1 `bedwars.yml`

Root keys:
- `leaderboard`
- `parkour-leaderboard`
- `match-events`
- `lobby-parkour`
- `arenas`

`parkour-leaderboard` world fallback:
- if no explicit world is set on `parkour-leaderboard`, use `lobby-parkour.world` first
- only then fall back to the generic BedWars leaderboard world resolution

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
- `chaos`
- `in-this-economy`
- `april-fools`

`moon-big` runtime note:
- use the gravity attribute for the low-gravity effect
- only apply `Slow Falling I`

`in-this-economy` runtime note:
- `fireball`, `bed_bug`, and `dream_defender` stay purchasable at `4x` their normal price
- diamond and emerald map generators should drop gold instead, keeping their slower generator cadence

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
- `LOCKPICK`
- `UNSTABLE_TELEPORTATION_DEVICE`
- `MIRACLE_OF_THE_STARS`
- `TOWER_CHEST`
- `STEEL_SHELL`

Behavior notes:
- `FLAMETHROWER`
  - cone attack in front of the player
  - uses particles for the area preview and directly damages/ignites targets in the cone
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
  - should arm on placement, trigger when an enemy player moves onto the mine or the surrounding 3x3 horizontal area, and detonate through the normal TNT explosion path
  - should use placed-block tracking so it can be broken, dropped, rolled back, and chain-exploded like other BedWars placed blocks
- `LOCKPICK`
  - rotating held item used on enemy team storage inside that base's radius
  - right-clicking a normal chest/trapped chest starts a 10-second countdown above the chest, then grants that player 60 seconds of access to that base team's normal chests
  - starting a normal-chest lockpick should show a big title to all online players on that team saying `<robber> is robbing your team chest`
  - right-clicking an enemy ender chest opens a team-member target GUI unless the player already has active lockpicked access for that base team
  - starting an ender-chest lockpick should show a big title only to the selected target saying `<robber> is robbing your ender chest`
  - lockpick countdowns and the 60-second access windows should show a timer above the clicked chest that is only visible to the player who triggered that lockpick
  - ender chest target selection starts a 20-second countdown above the chest, then grants 60 seconds where right-clicking that base team's ender chests opens the selected player's fake BedWars ender chest until the timer expires
  - once the ender-chest access timer expires, those enemy ender chests should revert to opening the viewer's own fake ender chest again
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
- `TOWER_CHEST`
  - chest deployable that builds a fixed wool tower aligned to player facing
  - uses team wool plus placed ladders, follows the fixed 7-layer popup-tower layout in `GameSession.TOWER_CHEST_LAYERS`, only fills air blocks inside the map, ignores anti-build placement restrictions, and removes the center chest shortly after placement
- `STEEL_SHELL`
  - purchased as a held item using a `NETHERITE_BLOCK` icon
  - right-click activation builds a temporary bedrock prison around the user for 10 seconds if every shell block fits in air inside the map
  - while active it applies `Resistance V` and then restores any previous resistance effect when the shell expires

### 2.9 Match Event Workflow

Prestart event control lives in the team-assign menu:
- left-click `Game Events` to enable or disable events for that match
- right-click `Game Events` to force a specific event or switch back to `Auto Random`

Forced event selection is prestart-only state and should not be conflated with the runtime `activeMatchEvent`.

### 2.10 Setup Workflow

Manager: `BedwarsSetupManager`

Create arena:
- `/bw setup new <arenaId>`
  - seeds `game-lobby` to `0 73 0` in the arena world by default

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
- Keep BedWars config parsing tolerant of legacy casing and aliases where the loaders already support them.
- Shop + rotating config are both active after reload because of merge behavior.
- If stats are disabled for a session, progression/wins should not be awarded.
- Outside a running BedWars match, protected BedWars worlds should still block casual terrain changes like farmland trampling unless the player is an allowed editor.
- Outside a running BedWars match, players should not be able to rotate, take from, or break item frames in protected BedWars worlds unless they are allowed editors.
- During a running BedWars match, normal chests/trapped chests inside a team's base radius are locked to that team until that team's bed is destroyed; afterward they are open to everyone.
- If a pending respawn later turns into a true elimination because respawns are no longer allowed, final-death and final-kill stats should still resolve from that original death.
- If a running participant quits, they should be removed from the match immediately; if that was the last remaining player on their team, normal team-elimination and win-resolution must still happen from that quit.
- If a participant or spectator quits from the active arena world, move them to the arena lobby state before logout so reconnecting does not leave them stranded on the map.
- If a player joins while outside the running match but still inside a BedWars arena world, non-editor players should be snapped back to that arena's lobby on join as a safety net.
- `netherite_spear` movement boost reuse must be hard-blocked for 5 seconds with native `NETHERITE_SPEAR` cooldown plus short follow-up velocity suppression on denied attempts; do not rely on message-only listener gating.
- Lobby-mode prestart should build a temporary 15x15 barrier platform centered under the resolved `map-lobby` location and restore the original blocks when the session leaves lobby/starts the match.
- Match end cleanup should return all remaining arena spectators to the arena `game-lobby`; `map-lobby` is for prestart/spectate flows, not post-match cleanup.
- `/bw game spectate` can only be run by a player already standing in the active BedWars world.
- When there is no active BedWars session, the main BedWars lobby world should play a `BLOCK_AMETHYST_BLOCK_CHIME` ambient sound at `0 90 0` for players in that world at random intervals between 30 and 60 seconds.
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
