# Game Design Document (GDD)

## Halls of Carnage \- An Om Nom Lands Experience

### Summary
The following document describes a minigame for Minecraft Om Nom Lands private SMP.
The document will be handed to an AI agent like Codex to develop the game.

### Agent note
While developing the game, make a standalone document describing the exact state of development in case the user runs out of credits during the development, so another instance of the agent can resume it later.

### Game Description
Halls of Carnage (name of the game) is a game simillar to games like R.E.P.O. or Grain Rot, where players are taken into randomly generated dungeons with monsters, where they need to reach some goal to proceed, like gathering scraps.

The difference from these games is simple, instead of normal play, there are scenarios, which offer unique level sets where players have to go through a number of floors to win the game. Every few floors is a camp, where structures can be placed, gear can be upgraded and so on. If the team dies, they restart from the beginning, however the camp layouts are saved, allowing progression and abillity to dive into deeper floors.

Game should be developed dynamically, allowing for future content integration.

Also instead of score, the game defines shame, which is accumulated through various means. The game records the shame and the shame leaderboards are then in the lobby, ascending.


# The Game
## Dimension
The game takes physical place in om:halls_of_carnage dimension, which was already created by OmVeins plugin. You have full control over the dimension except that setting spawnpoint via bed and lighting nether portals was disabled.
Do not do anything that affects other dimensions or players within them.

To handle multiple games at once, make it so each session is just at a different place. When unloading, dont forget to clean to place up.
## Target version
As of writing this, the server is on version 26.2, however that might have changed, so refer to the pom.xml file of this project. There is no need to support previous versions, but when updating, it should be made sure the game remains compatible.
## Database
OmGames already has an SQL database, use it for needed stuff.
## Lobby and player handling
The game should have a lobby, akin to main menu. A player will build on at coordinates 0 70 0, do not place stuff at least 1000 blocks near. Lobby will feature a villager which will open main menu for players, look into bedwars part of the plugin for example.
It will also contain a return point which lets players return back to overworld, that will be provided by OmVeins.
OmVeins also ensures player will have spawnpoint in the lobby when player enters the dimension, but I would request OmGames handles setting spawn point in the lobby during players stay in the dimension. For example when player leaves the game or disconnects. Again look into bedwars portion of the project.
The game should also support multiple games being played at the same time, this is a new behaviour not present in bedwars.

Players should be in adventure mode.

Generate lobby commands simillar to bedwars, so admin can build the main menu lobby.
## Scenario
A scenario defines structure of the game.
They are located under resources/hallsOfCarnage/scenarios. There is already one under there so you can look at how it's structured.
It contains how the campaign is structured. While each dive attempt randomizes the floors by generating a new seed, the level types and camps stay the same.

Also contains item types which are present in the scenario, choosing whitelist approach for which gear can appear/be crafted during the scenario. Same goes for camp buildings
Blueprint drop pools are scenario-owned. A scenario may define global normal/rare blueprint pools and may override those pools per level type, so specific building blueprints can be found only in certain dungeon themes.
## Multiplayer
Game can be played in 1-6 players. When player dies in a multiplayer game, they become a ghost (not spectator, I dont want them to go out of bounds). And can explore the level still. However the inventory is blocked and all stuff dropped on the ground. Can damage monsters using hand. They are also still in adventure mode, but invisible. Particles are displayed in their place.
## Save Files
Save file is connected to both players and scenario. All players must be present for savefile to be played in case of multiplayer. If player leaves during the game, there will be 5 minute grace period before the game shuts down.
Games can be saved in camps and starting area.
If scenario is finished by beating last floor, the save is marked as completed and records itself into player history, becoming unplayable.

When creating a savefile, scenario is first picked and then players present in the lobby.
## Items
Players have limited inventory of armor slots and only the hotbar row. Halls player equipment and utility items are unstackable unless an item file explicitly says otherwise. Dropped items on the ground are removed and instead a physics driven item drop is created. They can use Item Displays and Interaction entities for a smooth experience. Players can pick them up with right-clicking by empty hand.

Items are located in the resources/hallsOfCarnage/items/ folder. Look into there for examples. There is also a chest in the elevator, giving players access to 27 slots for floor transfer. The contents of the chest are lost if players loose.

One type of items are scraps. Those can be taking into a chute in the elevator stored into separate storage, which allows for stacking up to 99 and is then used for crafting and building. Collecting scrap this way also rewards coins. Scrap cannot be taken out of the storage, working more akin to "cloud shared storage". Also lost on game over.
## Level Generation
Each level has its own type of generation. Each level has the elevator at the center.

Layouts are stored under reources/level and level types under resources/hallsOfCarnage/level_type.
Type is like a biome. It defines what kind of modifiers can appear in the type and what kind of mobs do too.
The layouts contain X for filled, like walls and pillars, of course there is also an unspecified wall around the rooms. O is open space.
The level type defines from what material the walls, floor and ceiling are built from.
Level types can also configure decorative vegetation density and weighted vegetation ids from `resources/hallsOfCarnage/vegetation`.

A floor config in scenario sets its size and type and difficulty. Elevator is always in the middle of the floor and everything generates around it. All rooms are then connected with corridors. 
The generation algorithm for corridors is level type specific, but each room generates a connection point which then tries to be connected. Rooms try to create connection points on nearby rooms when generating too, so they can connect. 
If corridor intersects another throughout the generation, then it stops expanding and is taken as finished. There's also a small chance (5%) for each room to get another connection, which tries to connect to another room or corridor. Corridors must connect to free spaces and the space must not be occupied by unpassable stuff.

Rooms are 5 blocks tall, corridors 3 blocks tall.

First rooms generate, then corridors, then traps, then decorative vegetation, then items and breakables.

There are three types of corridor generations which level types can pick from.
#### Normal
Straight corridors with 90 degree turns are created between the rooms, they are 1 block wide.
#### Cave
Natural bendy corridors generate between the rooms. They are 1-3 blocks wide per need and must allow passage on bends.
#### Maze
A maze is generated and the rooms are set into it, allowing entrance on the connection points.
#### Backrooms
A maze-like open hall field is generated, then long internal wall runs are added while preserving reachability.

### Elevator
Elevator is a 5x4x5 inside area with walls around it, making the total shell footprint 7x4x7. Walls:
1233321
2     3
4     5
4     5
4     5
2     3
1233321

1 is a reinforced deepslate, shouldn't really be seen unless generation around uncovers it
2 are red nether bricks
3 is deepslate bricks on bottom and top and two tuff bricks in the middle
4 are machines, the upper one is the scrap tube, middle is a button for going deeper and lower is the chest
5 elevator doors, they should be waxed weathered copper bars. It should have opening and closing animation and there is a corridor connection point in front of the middle part.

Floor is from packed mud and ceiling from smithing tables, make sure players cant open their GUI.

### Start Floor
Start floor is always the same, but can have modified appearance by level type. It should be always defined as first floor in the scenario and is in resoruces/level/special/start_floor.txt.
It is a single room with elevator next to it. There is a breakable barrel with random building blueprint inside it.

### Exploration Floors
Exploration floors are the main part of the game. Players need to fulfil a quota to ride deeper. It contains rooms with objects and other stuff.
It uses the complex corridor generation described earlier.

Rooms are generated based on level type from resources/hallsOfCarnage/level/<level_type>/

Going to an exploration floor picks 3 modifiers based on difficulty during the transition floor. These floors also contain monsters, their spawning is described later.

### Combat Floors
Combat floors are defined under resources/hallsOfCarnage/level/special/<id_from_scenario> and appearance is affected by the level type. They normally appear at the end of an
scenario, but some may put them also into different parts. The elevator is next to the room simillar to start floor and instead of normal goal, players must activate the room from a terminal in the middle and
fight waves of randomized mobs defined by a pool from the scenario.

Once all enemies are defeated, the elevator can be taken deeper.
### Camp Floors
Camp floors are respites for players. Each scenario has one shared camp layout for the entire campaign instead of separate random camps per camp floor. The camp is configured by the scenario and can use a large text layout such as `resources/hallsOfCarnage/level/camps/camp_untold_depths.txt`.

The camp layout uses normal open/blocked room markers plus special markers:
- `L` is the camp elevator link point.
- `D` is a locked camp door.
- `C` and facing markers (`N`, `S`, `E`, `W`) define build plots.

Camp rooms behind locked doors start inaccessible. Right-clicking a locked door tells players what build plots are in the room behind it. Shift-right-clicking the door while the party has a key permanently unlocks it. If both sides of a door are already unlocked through other paths, that door can be opened for free.

Players can build buildings on build spots scattered throughout the shared camp. There are 3 types of slots: Small (1x1), Medium (3x3), and Large (5x5). Smaller buildings can be built on larger spots. The shared camp is saved across camp visits, but it resets when the team suffers a full hard reset after running out of team lives.

Camp floors replace the normal coin quota display with a key counter. When the party arrives at camp, extra carried coins are deposited into the camp bank. The scenario defines a key-cost sequence, for example 30, 40, 50. Bank progress accumulates across camp visits and grants a key each time the next threshold is reached. Keys are then spent to unlock reachable camp doors.
### Transition Floors
Transition floors are like loading screen. Which should last at least 10 seconds or more if the game needs to. It is in the elevator with partiles going around.
If going to exploration floor, modifiers are also picked here and displayed as title like a "gambling machine display."

### Victory Floor
A special transition floor at the end of the game, which tells players they won and then they move back to the lobby.
## Level Types
Level types define look of the rooms, enemies and possible modifiers, like biomes almost.
Saved under resources/hallsOfCarnage/level_type
The doc does't state much as they are in concept stage right now.
Level types may define decorative vegetation using a chance and weighted vegetation ids. Vegetation is display-only decoration, not normal placed blocks, and should not use interaction entities or hitboxes. It can appear in rooms and corridors, but must not generate inside holes or on ground-trap cells.
### Howling Corridors
Your basic minecraft dungeons. Zombies, skeletons and so on.
### Frozen Halls
Frozen Caves.
### Deep Crypt
Desert temple.
### Infernal Chambers
Nether-blackstone halls with wider corridors, heat-lit walls, and harsher monster pools.
### Factory
Industrial open halls with machinery-like pillars and room-local open-hall generation.
### Backrooms
Yellowed liminal halls with maze-style open spaces, long wall runs, and sparse unsettling monster pools.
### Sewer
Wet brick service tunnels with wide 5-block corridors and a 3-block liquid channel. Rooms can generate broad contiguous puddles, including along room borders, and adjacent puddles/channels should merge without separating walls; liquid is configured by level type so water is used now and lava can be used by future content. Bear traps and proximity mines are blacklisted from this level type. Sewer bubbles and geysers use display-only bottom fixtures in puddles; geysers knock entities upward without direct damage.
### Library
Old shelf-lined halls with mixed circulation. Normal corridors use wide-corridor generation, while some connectors become 1-wide, 2-block-tall vents that can reconnect into other corridors.
### Bunker
Reinforced service halls. Generation follows the sewer-style main-corridor approach without liquid, using 3-wide bunker corridors with smaller branches into rooms.
### Other levels
Of course, other types will be implemented throughout development.
## Traps
Levels can generate various traps based on level type.
### Hole
Holes can generate, they are 10 blocks deep with black concrete at the bottom, which instakills the player. If ghost falls there, they will be teleported to the elevator. If a hole restricts access to some other part of the room, then a spruce fence bridge must be generated.
### Swinging blades
Blades swing accross the room from one side to the other, insta-killing stuff
### Bear trap
Just a bear trap, must not generate in a way that restricts access. Deals a lot of damage.
### Wall spikes
Generates on the walls, spikes periodically pierce entities in front of it, dealing a lot of damage.
### Proximity mine
Bear trap, but better.
### Steam vent
Factory-only floor vent that alternates between safe and harmful intervals. During the harmful interval it fills a 3x3 area with steam/smoke and damages players and monsters passing through it.
## Modifiers
Exploration floors have modifiers, these are defined by level types, allowing unique modifiers for specific types.
However most are shared. There are good and bad modifiers. Good are yellow, bad are red. They are picked based on the difficulty, higher difficulty means lower chance for good ones.

They are not defined in the resource files, so please add resource files for them.
### Good
- Free - nothing
- Lower quota - decreases the current floor coin quota by 5 coins
- Less enemies - 25% less enemy spawns
### Bad
- More enemies - 25% more enemy spawns
- More traps - 50% more traps
- Less loot - 25% less loot
- More sculk - triple sculk generation
- Special enemy - adds a special enemy from special enemy pool to the enemy pool based on level type
- More rooms - adds 3-5 more rooms (this is bad, because the treasure is more spread)
- Longer corridors - rooms generate further apart. This effect shouldnt be as powerful when maze corridor generating is active.
- Death fog - Deadly fumes build up, resulting in wither effect after 10 minutes of entering the floor. Players will be warned throughout.
- Falling ice - In some rooms, icycles periodically fall from the ceiling as a special trap. Unique to Frozen Halls.
- Poison darts - Adds a new poison dart trap. Unique to Deep Crypt.
- Steam vents - Makes Factory steam vent traps more common.
## Buildings
Players can build buildings in camps. To build a building a blueprint is needed. Buildings have sizes of small, medium and large (1x1, 3x3, 5x5).
They can be built on special spots in camps. Smaller buildings can be built on larger spots. These buildings last over game overs and offer a way to outpace the increasing difficulty of the game.

More building will be added later.

Demolishing a building costs nothing and returns the building blueprint. If that building spent matching blueprints on level 2/3 upgrades, those spent upgrade blueprints are returned too. Other upgrade resources are not returned.
Building a building costs nothing, but blueprint is consumed.
Building has upgrades defines under resources/hallsOfCarnage/buildings/<building_name>/level_<number>. The file contains cost of building (scrap materials needed).
The block layout as a visual represantation of it and other info needed for them to function. Figure this out yourself.

All buildings have 3 levels.

### Camp Station
- Size: Station (7x7 reserved camp plot)

Each camp has one permanent Camp Station. It is always present, has no blueprint, has no levels, cannot be deconstructed, and provides all food, weapon, utility, and armor recipes that were previously split across Cooking Pot, Weapon Bench, and Armory.
Camp Station crafting is split into food, weapon, utility, and armor views. Recipes are locked behind a scenario-defined research tree. Root research nodes are available at campaign start; every normal camp arrival grants one research point for each exploration floor cleared since the previous camp, and researched nodes persist in the team save.
### Grindstone
- Size: Large

Each run, can sharpen one held weapon, increasing its melee damage. The upgrade strength is based on level.
### Forge
- Size: Medium

Each run, can repair one held Halls item. It restores 30% of the item's maximum durability per building level.
### Storage Locker
- Size: Small

Has 9/18/27 storage slots by level. Allows storing items for future runs. Upgrading to level 2 and level 3 requires another Storage Locker blueprint in addition to scrap costs.
### Mycelia Farm
- Size: Small

Grows basic food for free. Bigger level = more.
### Potato Farm
- Size: Small

Behaves like Mycelia Farm, but grows potatoes.
### Carrot Farm
- Size: Small

Behaves like Mycelia Farm, but grows carrots.
### Research Table
- Size: Medium

Common building. Upgrading requires another Research Table blueprint. Blueprints can be deposited into it for blueprint points: normal blueprints give 1 point and rare blueprints give 2 points. At level 1, fabricating a normal blueprint costs 5 points. At levels 2 and 3, fabricating a normal blueprint costs 3 points. Level 3 unlocks rare blueprint fabrication for double the normal point cost.
### Alchemy Cauldron
- Size: Medium

Common building. Upgrading requires another Alchemy Cauldron blueprint. Converts stored scrap into another scrap type at 4:1, 3:1, and 2:1 by level.
### Deconstructor
- Size: Medium

Destroys held recipe-backed Halls items and returns random stored scrap from direct recipe scrap costs. Refund chance is 30%, 40%, and 50% by level.
### Elevator Drill
- Size: Large

Reduces exploration floor coin quotas while built. Each drill applies a multiplicative quota multiplier based on level: level 1 is 0.9, level 2 is 0.8, and level 3 is 0.7. Multiple drills stack multiplicatively.

### Scanner
- Size: Small

Tells you modifiers for the next floors based on the level.
### Health Totem
- Size: Large

Has 1 charge per run. Increases one player's max health by 2 per level until the next camp arrival. Multiple Health Totems can stack on the same player when they are built on different plots.
### Speed Totem
- Size: Small

Has 1 charge per run. Increases one player's movement speed by 5% per level until the next camp arrival. Multiple Speed Totems can stack on the same player when they are built on different plots.

### Sculk Purifier
- Size: Medium

Has 3 charges per run. Removes sculk from the player who uses the charge, scaling by level. Upgrading to level 2 and level 3 requires another Sculk Purifier blueprint in addition to scrap costs.
### More
System must allow more to be added in the future. If anything comes to you during development, add it into this doc.

## Items
Items generate throughout levels, most of the time as breakables, but also can generate just as themselves. Coins can also generate and add 1 coin to shared counter when picked up.
All items except scrap should be editable as files under resources. Also make it so where it makes sense, id for item model can be applied, so if made, a custom model could be applied. Default is nothing of course.
### Scrap
Game has 4 types of scrap: wood, iron, diamond and redstone. Adding scrap into the storage in elevator rewards 1 coin.
### Breakable objects
Stuff like barels, chests, tables, chairs and so on can generate. Breakables should be entity-driven props, not normal placed loot blocks. Use display entities for visuals plus interaction or hitbox entities for damage, collision, and player pushback so players cannot walk through them. When broken, they can drop the same physics-driven unpicked item entities used by normal floor loot.
### Food
Food is meant for regenerating lost health, as natural regeneration is turned off (you must disable this yourself).
Some food can apply status effects.
Some food can reduce sculk pressure through item stats. Current baseline ingredient foods include raw mycelia, potato, and carrot; stronger meals should be allowed to require earlier food items as recipe prerequisites.
### Melee
Swords, axes, spears and so on. All have durability, which is not a normal minecraft durability.
### Utility
Shields, totems of undying and other stuff. Utilities can have custom durability, losing durability on successful use instead of always being consumed.
### Armor
Armor. Of course, stuff like melee weapons, utility and armor can have special attributes, to make things spicy. Define this in the files.
### Blueprints
Sometimes a blueprint can be found, which can be used for buildings.
## Monsters
Monsters slowly flood the dungeon. The amount and max current spawns are defined by size of the level and difficulty/modifiers.
On exploration floors, monsters spawn in places not visible by players currently. If possible on technical level, when player interacts with stuff such as breaking or depositing scrap, they should be alerted of the location and walk there for a small period of time until loosing interest.
Monster types are based on the level type. Level type also defines pool of special mobs, but those dont spawn unless modifiers are active or players survive more than 15 minutes on a floor. Direct spawn attempts stay on a fixed interval. After 3 minutes on an exploration floor, successful monster spawns reduce the cap-extension cooldown by 1% of its base length until it reaches a 5-second minimum.
On fight floors, monsters "come in" (spawn) through fake doors on the walls.
Large monsters such as ravagers should be tuned below vanilla lethality so they act as pressure enemies instead of instant run-ending walls.
## Sculk
Based on difficulty, sculk patches can appear and replace parts of the levels. Standing on sculk blocks or veins raises the player's sculk pressure. Sculk is saved between floor and save loads. The alive player with the highest sculk pressure controls the chance for a warden to spawn instead of a normal enemy; it should not directly apply potion effects, hunger changes, or item-use restrictions.
## Hunger
Hunger is not present in the game. Make it so the bar is always full so player can sprint and disable natural regen.
## Compass
Above hotbar on message line should be something akin to a ui, which will tell players their scrap amounts, time on the level, level number, sculk level and so on.
## Floor progress
### Reviving
If player dies and becomes a ghost, it gets revived on the next floor without their stuff.
### Loose conditions
Players have shared team lives, configured by scenario and defaulting to 3. If all players become ghosts, the game fades into a game-over transition. If the team has reached at least one camp and still has a life left, one life is consumed and the last camp save is restored. The shared camp does not reset in this case.

If the team wipes before reaching the first camp, the run hard-resets to floor 1 without consuming a life. If the team wipes with no lives left, the players are moved to floor 1, the shared camp resets, and they can begin another run.
## Sounds
Add sound effects to stuff. You have full freedom over the choice as long as it seems suitable.
## Shame
Shame is an equivalent of score, except you want lowest one. Shame accumulates for each elevator ride, for each death, for each building built/upgraded. It is lowered for killing mobs and getting coins, but it should never go under 0.
## Statistics
Gather useful and fun statistics unless it makes the game run slower. Can be useful later.
## Afterword
You are free to change any of the resource text files if it can improve the structure of the game.
The resource files are not finished and should be taken as example. Finish them yourself, generate the room layouts and so on, but remember a human dev should be able to easily add/edit their own.
You can also change this document to keep better documentation for human devs.
