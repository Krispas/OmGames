

# Game Design Document (GDD)

## Deepfrost \- An Om Nom Lands Experience

## Table of contents {#table-of-contents}

[**Table of contents	1**](#table-of-contents)

[**1\. Introduction	2**](#1.-introduction)

[1.1. Scope of the document	2](#1.1.-scope-of-the-document)

[1.2. Elevator pitch	3](#1.2.-elevator-pitch)

[**2\. Game Overview	3**](#2.-game-overview)

[2.1. Game concept	3](#2.1.-game-concept)

[2.2. Audience	3](#2.2.-audience)

[2.3. Genre	4](#2.3.-genre)

[2.4. Setting	4](#2.4.-setting)

[2.5. World structure	4](#2.5.-world-structure)

[2.6. Player	4](#2.6.-player)

[2.7. Core loop	4](#2.7.-core-loop)

[2.8. Look & Feel	4](#2.8.-look-&-feel)

[**3\. Gameplay	4**](#3.-gameplay)

[3.1. Objectives	4](#3.1.-objectives)

[3.2. Progression	5](#3.2.-progression)

[3.2.1. Difficulty curve	5](#3.2.1.-difficulty-curve)

[3.3. Play flow	5](#3.3.-play-flow)

[3.4. Difficulty	5](#3.4.-difficulty)

[**4\. Mechanics	5**](#4.-mechanics)

[4.1. Rules	5](#4.1.-rules)

[4.2. Game universe	5](#4.2.-game-universe)

[4.3. Physics	5](#4.3.-physics)

[4.4. Economy	5](#4.4.-economy)

[4.5. Character movement	6](#4.5.-character-movement)

[4.6. Player interaction	6](#4.6.-player-interaction)

[4.6.1. Game menus	6](#4.6.1.-game-menus)

[4.6.2. Saving	6](#4.6.2.-saving)

[4.7. Assets	6](#4.7.-assets)

[**5\. Graphics and audio	6**](#5.-graphics-and-audio)

[5.1. Audio system	6](#5.1.-audio-system)

[5.1.1. Game music	6](#5.1.1.-game-music)

[5.1.2. Audio look & feel	6](#5.1.2.-audio-look-&-feel)

[**6\. Story and narrative	6**](#6.-story-and-narrative)

[6.1. Backstory	6](#6.1.-backstory)

[6.2. Main plot	7](#6.2.-main-plot)

[**7\. Enemies	7**](#7.-enemies)

[**8\. Game world	7**](#8.-game-world)

[8.1. Look & Feel of the world	7](#8.1.-look-&-feel-of-the-world)

[8.2. Levels	7](#8.2.-levels)

[8.3.1. Safehouses	7](#8.3.1.-safehouses)

[8.3.2. Scrapyard	7](#8.3.2.-scrapyard)

[8.3.3. Howling Halls	7](#8.3.3.-howling-halls)

[8.3.4. Crumbling Ruins	7](#8.3.4.-crumbling-ruins)

[8.3.5. Pulsing Cavern	7](#8.3.5.-pulsing-cavern)

[8.3.6. More	7](#8.3.6.-more)

[**1\. Level editor	7**](#level-editor)

## 

## 

## 

## 

## 1\. Introduction {#1.-introduction}

### 1.1. Scope of the document {#1.1.-scope-of-the-document}

Who’s this document meant for? Who’ll read it? The dev team? Stakeholders? Investors? 

This document is made for several people. Mainly the writer, MMG, admin and main developer of Om Nom Lands SMP (a private minecraft server plugins OmVeins and OmGames are developed for, the SMP focused on survival gameplay, with custom features and some minigames on the side in unusual mashup), Krispasi, prompt engineer working on mostly only on OmGames plugin this document is about and then his AI agents as well as a reference.

### 1.2. Elevator pitch {#1.2.-elevator-pitch}

Deepfrost is a quota-friendslop game. It is cooperative and players need to navigate sprawling procedurally generated stages, gather items and reach the end in time, while surviving extreme cold weather, using heat generators and defending themselves from monsters.

## 2\. Game Overview {#2.-game-overview}

### 2.1. Game concept {#2.1.-game-concept}

The game is based on runs (which can be saved in the middle and continued some other time, however, you need the full original team to continue the run\!). Runs will go through 5 (WIP number) procedurally generated stages which get progressively harder with the last stage containing some big challenge (boss? Environmental hazard?)

Each stage, players will have a quota based on the stage and the difficulty. The level will also be populated by monsters. There will be various resources scattered throughout the level. Some will be needed for the quota, some will be for crafting, monsters also can drop unique resources on rare occasions. Players on stage have limited time, because their heaters have a limited battery. Yes there are heaters based on playercounts which teams have. If the player is outside the heater radius, they will accumulate frost and start taking damage after some time.  
At the end of the stage is a safehouse. The players know its location from the moment they enter the game. The game also can branch, first and last stages are always predetermined, but for the other ones, players will get to choose from 1-3 stages (they know biome, danger level). If more than 1 choice is available, more safehouses will be available. However, to open a door to the safehouse, the players must deposit the quota items.  
Safehouse has no time limit, players here can save the game, sell their resources and craft stuff from recipes they have unlocked. For completing the quota, the group also gets some money to spend on upgrades/items.

At the end, the players can sell all their remaining materials and the game ends. The score will be calculated based on how much money they made through the entire run \* difficulty multiplier. (Even if they get game over)  
Runs can also unlock new recipes to craft on next runs (via achievements). This also counts for new classes (starting items/perks), new biomes/biome modifiers, potentially also monsters. And upgrades.

The goal of the game is to gather the biggest score and complete all achievements.

### 2.2. Audience {#2.2.-audience}

The game is made for enjoyers of cooperation, horde control, exploring and strategy games.

### 2.3. Genre {#2.3.-genre}

Cooperative survival game

### 2.4. Setting {#2.4.-setting}

A post-apocalyptic sci-fi setting, where the world is now frozen over and people live in underground silos. Players play as expedition teams sent out to gather fuels to keep humanity alive. The world also was heavily impacted by all kinds of mutations, creating some alien-like settings.

### 2.5. World structure {#2.5.-world-structure}

The game is semi-linear, the players move through a set amount of stages (5 seems like a good WIP number). There are alternative stages as described in previous points so players have some merging points. The stages themselves are semi-maze open world. (there could be doors through the stage maybe?) The stage is generated as a graph and then a level with the same structure is generated. Between each stage is a safehouse and a lobby.

### 2.6. Player {#2.6.-player}

The game is for 1-8 players, recommended amount being 4 (and the amount the game is mainly built around). Players have heaters based on their amount. 1 player has 1 heater. 2-4 players have 2 heaters, 5-6 have 3 heaters and 7-8 have 4 heaters. Players can gain various weapons (not regular ones like swords), craft them and so on. Players scavenge the map for materials.

### 2.7. Core loop {#2.7.-core-loop}

The team has to work together to explore, defend and scrap. There should always be someone who manages the heaters, someone who scouts for danger and of course the scrapers. Various playstyles are encouraged and these roles are not set in stone. During each stage, the team needs to collect materials to meet quota and their crafting needs. Select the next safehouse, travel to it without dying. The team loses only if everyone dies, as long as a single person makes it to the safehouse, rest is revived there. The safehouse can be used to plan the next stage.

### 2.8. Look & Feel {#2.8.-look-&-feel}

Although the game takes place in minecraft, I’d love it to have the post-apocalyptic style throughout the level design and custom models/textures for the game elements. (in alpha versions, these won't be present, but the design must count with them)

## 3\. Gameplay {#3.-gameplay}

### 3.1. Objectives {#3.1.-objectives}

The goal of the game as a whole is to unlock new upgrades and beat max scores and unlock achievements.  
The goal of each run is to get through all the stages alive, meeting their quota and not dying. They also need to make it on time.

### 3.2. Progression {#3.2.-progression}

To progress through runs, players need to collect scraps to meet quota’s and make it to the safehouses in time and alive.

#### 3.2.1. Difficulty curve {#3.2.1.-difficulty-curve}

The game has a difficulty setting, which makes the game harder and multiplies the gained score.

### 3.3. Play flow {#3.3.-play-flow}

Players will explore a stage for scraps and defeat monsters. In safehouses, they will prepare for the next stage. During the boss stages, players will have to defeat a boss instead of meeting quota.

### 3.4. Difficulty {#3.4.-difficulty}

Bigger difficulty will make it more common for stage modifiers to happen, monsters will also get stronger, however, more materials for crafting will spawn.

## 4\. Mechanics {#4.-mechanics}

### 4.1. Rules {#4.1.-rules}

Players move around, use their tools and weapons. Players should not be able to break blocks and get out of bounds.

### 4.2. Game universe {#4.2.-game-universe}

When a safehouse is entered, the previous stage is unloaded and a new one is generated, the actual block placing process should be parallel and slowed down, so it doesn't lag out the server. There is a main lobby dimensions and x amount of game dimensions, making it so more matches than one can be happening at the same time.

### 4.3. Physics {#4.3.-physics}

Default minecraft physics for movement.

### 4.4. Economy {#4.4.-economy}

Players have 2 types of scrap, fuel scrap and crafting scrap. Fuel scrap is for the quota like coal, fuel, uranium and so on. Then there is crafting stuff, titanium, vires, batteries and similar, which is used to craft stuff. Players receive money for selling scrap (quota included). Using the money, they can buy various other resources and team upgrades. At the end of the game, the score is multiplier times the amount of money the crew made throughout the whole run.

### 4.5. Character movement {#4.5.-character-movement}

Players will navigate throughout the generated stages, but should not be able to get out of bounds.

### 4.6. Player interaction {#4.6.-player-interaction}

Throughout stages, there will be loot nodes scattered, which players can harvest, certain nodes require certain conditions, for example some need to be heated up, others need a wave of enemies defeated, some are free and some may have a puzzle near them.

There can be other elements potentially, like doors and keys.

#### 4.6.1. Game menus {#4.6.1.-game-menus}

In the safehouse, there will be trading interfaces players can access.

#### 4.6.2. Saving {#4.6.2.-saving}

In the safehouse, there will be an interface which lets players save their game.

### 4.7. Assets {#4.7.-assets}

Alpha version will include mostly minecraft models and textures. Enemies will surely have minecraft models. Bosses will have custom models, hitboxes and so on. Custom sounds can be provided and later the goal will be to have custom texture for each item.

## 5\. Graphics and audio {#5.-graphics-and-audio}

### 5.1. Audio system {#5.1.-audio-system}

The game will use both custom and minecraft audio. Players will talk through in-game voice chat supplied by simple voice chat mod.

#### 5.1.1. Game music {#5.1.1.-game-music}

There will be exploration music for each biome, safehouse music, which will be different for each safehouse and boss music for each boss. The lobby is silent.

#### 5.1.2. Audio look & feel  {#5.1.2.-audio-look-&-feel}

The audio should convey mystery, unknown, alien environment, danger and mainly cold.

## 6\. Story and narrative {#6.-story-and-narrative}

### 6.1. Backstory {#6.1.-backstory}

A long time ago, humanity was destroyed, what did happen? Nuclear war? Aliens? No one knows, but the world is now covered in abysmally low temperatures and mutated monsters. Humanity now lives in underground silos, struggling for energy sources.

### 6.2. Main plot {#6.2.-main-plot}

You are playing as a group of fuel hunters sent by your silo to gather fuels. Story has no progression.

## 7\. Enemies {#7.-enemies}

The game will feature many enemies which will be added throughout the course of development.

## 8\. Game world {#8.-game-world}

### 8.1. Look & Feel of the world {#8.1.-look-&-feel-of-the-world}

The world is frozen over, in parts alien, wasteland, old structures made by humans and so on.

### 8.2. Levels {#8.2.-levels}

Just as each one of their names say, briefly describe the levels of the game (If there’s any).  Levels are more of a theme the game will be generated around.

#### 8.3.1. Safehouses {#8.3.1.-safehouses}

Structures built by humanity, post-apocalyptic steampunk like style.

#### 8.3.2. Scrapyard {#8.3.2.-scrapyard}

Old scrapyard full of junk, slowly collapsing over time.

#### 8.3.3. Howling Halls {#8.3.3.-howling-halls}

Endless maze-like corridors

#### 8.3.4. Crumbling Ruins {#8.3.4.-crumbling-ruins}

A crumbling ruins of a city

#### 8.3.5. Pulsing Cavern {#8.3.5.-pulsing-cavern}

Icy caves with alien flora and lifeforms.

#### 8.3.6. More {#8.3.6.-more}

More and more levels can be added to the games. 

1. ## Level editor {#level-editor}

The game will feature a level editor for the developers, where they can work on the modular pieces of the game. More will be specified during the development.