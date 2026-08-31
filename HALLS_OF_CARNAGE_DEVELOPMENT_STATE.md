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
- `/hoc sessions` lists active session ids, scenarios, participant counts, and origins.
- `/hoc stop <session_id|*>` stops sessions and restores changed blocks.
- If all participants in a session are offline for the configured grace period, the session is cleaned up automatically.

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

## Resume Notes

- Keep Halls code isolated from BedWars, Egg Hunt, Chess, Bank, and Random packages.
- Do not use `OmVeinsAPI` during startup.
- Do not place lobby blocks near `0 70 0`; the lobby is human-built.
- Session build origins should stay at least 1000 blocks away from the lobby; current default first origin is `2000 70 0`.


## Notes from human reviewer (you are free to remove these once completed)
- I tried running the game. Elevator generates completely wrong. By 5x5 I meant inside, not the whole structure.
- I know you aren't there yet, but I should better specify the breakables, edit the GDD with this. Breakables should not be blocks, but entities simillar to unpicked items. And unpicked items should interact by not falling through. There should also be some kind of hitbox which pushes player away if they try to go through them.