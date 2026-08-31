# Halls of Carnage Development State

Last updated: 2026-08-31

## Implemented

- Initial Halls of Carnage plugin foundation is being developed under `src/main/java/krispasi/omGames/hallsofcarnage/`.
- Runtime data folder is `plugins/OmGames/HallsOfCarnage/`.
- Bundled config/resources are copied on first run without migration logic.
- Scenario files are loaded from `plugins/OmGames/HallsOfCarnage/scenarios/`, seeded from `src/main/resources/hallsOfCarnage/scenarios/`.
- Shared SQLite database `plugins/OmGames/OmGames.db` is used for Halls shame/history tables.
- Bukkit command root is `/hoc`.
- `/hoc start <scenario> [player...]` now creates a lightweight active session, builds a temporary start floor/elevator area at an allocated session origin, teleports participants there, and keeps block snapshots for cleanup.
- The temporary elevator shell now uses a 7x7 outer footprint around a true 5x5 interior, matching the GDD. The front connector starts outside the copper-bar door wall.
- `/hoc sessions` lists active session ids, scenarios, participant counts, and origins.
- `/hoc stop <session_id|*>` stops sessions and restores changed blocks.
- If all participants in a session are offline for the configured grace period, the session is cleaned up automatically.
- Halls world natural regeneration is disabled by gamerule and players are kept at full hunger/saturation for sprinting.
- Smithing tables in the Halls world are blocked from opening their GUI, so generated elevator ceilings are inert.

## Current Scope

This is the first implementation slice. It focuses on:

- lifecycle wiring,
- lobby spawn/menu-villager setup,
- scenario discovery,
- basic player handling in `om:halls_of_carnage`,
- shame leaderboard persistence,
- resource schemas that future dungeon/session systems can build on,
- minimal temporary session runtime and start-floor generation.

## Not Yet Implemented

- Dungeon generation.
- Full multi-floor session runtime.
- Elevator transition logic.
- Ghost death state.
- Physics-driven item drops.
- Scrap storage and camp building runtime.
- Combat/exploration/camp floor gameplay.
- Sculk, traps, modifiers, monster flood systems.
- Entity-driven breakable props and physics-driven unpicked item entities with collision/pushback.

## Resume Notes

- Keep Halls code isolated from BedWars, Egg Hunt, Chess, Bank, and Random packages.
- Do not use `OmVeinsAPI` during startup.
- Do not place lobby blocks near `0 70 0`; the lobby is human-built.
- Session build origins should stay at least 1000 blocks away from the lobby; current default first origin is `2000 70 0`.


## Latest Slice Notes

- Addressed the reviewer note that the elevator generated too small. Existing server-side copied resources do not need migration; `/hoc start` uses the Java generator change immediately.
- Moved the breakable-object clarification into the GDD. Future item/breakable work should build entity props and physics item drops rather than placing ordinary loot blocks.
