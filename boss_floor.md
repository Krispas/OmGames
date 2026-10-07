Boss floors are replacement for fight floors. It works simillar to camps, as that the elevator leads into a single room which serves as the boss arena. In the middle of the room is a boss spawner (lets say its some kind of totem, use custom model for it, like breakables)
Once left clicked, the spawner will vanish and spawn the boss in its place, more specifically, the pillar will retract into the ground, then explosion particles will appear, masking the spawn-in of the boss.
Once boss is active, the entrance to the room is sealed, meaning players need to beat it to proceed.
Once defeated, entrance opens and elevator can be started to leave the floor.
The bosses do not use classic minecraft mobs, like normal monsters, instead they are fully custom, they need a hitbox, proper detection of all kinds of mechanics such as poison clouds, spears, arrows and so on. Bosses have health bars and should be data driven in the new boss folder, so I can adjust their stats on fly.
After defeating a boss, it takes 3 seconds for the door to open, make it so particles are created when door closes and opens.
Bosses have health bars.
Data files of bosses contain "multiplayer-hp-boost" which multiplies their hp multiplicatively based on the value, the default is 1.3.
Bosses have animations for their attacks and actions.
Bosses should have an AI base which I can use to make more versions of the same boss, something like zombie is used both for zombies, zombie vanguard and splinters.
Bosses have a state machine that cycles through attack with preconfigured cooldown between them, the cooldown can be different after each attack.
Use item models for the custom models, dont forget the origin of the rotation is not in the middle of the objects, read about it more in docs/wiki if needed.
The boss room is 10 blocks tall instead of the normal height.