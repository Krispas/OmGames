Endless mode is a special kind of scenario, more specifically a scenario type so we can define more endless modes in the future when we add new content.

For example we have the base game and a "DLC" scenarios.

Lets say there is a mixed endless mode, endless mode for base game content and endless mode for DLC content.

So for now, I want you to create 2 endless modes which are completely same right now. (but have different config files)

- Endless (Mixed)
- Endless (Base Game)

Research and buildings will be taken from UntoldDepths scenario, modifiers too.
For floors, make it so each floor, the floor type is random. There are always 3 exploration floor and then a camp. We this a module. 1 level type cannot repeat on two exploration floors in a module. The difficulty, holes, sculk, qouta and so on take from UntoldDepths, if you look at the scenario, there is a clear linear curve throughout its progression, use that curve.
Make it so camp layout and level type can be configured in each endless mode scenario, key stuff and so on.
For both of these endless modes, make it so there is always a boss module after 4 exploration modules. Boss module has a boss floor and then camp right away. There is a boss pool which the scenario takes information about bosses from (level type and boss type, difficulty is always 40). Scenario has a boss seed, which is same for all runs, the seed randomly determines the boss picked for the boss floor.
When getting full on game over, record the floor player ended on publicly.
Do not use shame system during endless mode.
In scenario pick screen, put these scenarios into a different row, separating them from normal ones, also use a different icon, as they cannot be completed.
Add a new window into the lobby menu, which shows leaderboards for endless scenerios and also normal ones, for normal ones use shame as metric, for endless, use level reached as metric.

Scenario has a level type pool, so we can define which level types appear.
If more level types get added in the future, it should work, but since save files can be only created in campsites, it should be no issue.
Research tree can change throughout time. Make it so the game remembers which nodes players researched and just support new nodes being added (if its not already programmed like this by default)

If you have any questions, ask me before starting the development on this feature.
