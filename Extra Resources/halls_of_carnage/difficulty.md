# Halls of Carnage Difficulty Reference

## Runtime Formulas

- Boss post-attack cooldown multiplier:
  - difficulty 20 = `0.70x`
  - difficulty 40 = `1.00x`
  - difficulty 80 = `1.30x`
  - below/above these anchors, the runtime extrapolates linearly and clamps only at a minimum of `0.10x`.
- Exploration modifier good-roll chance: `max(0, min(100, 50 - difficulty))%`.
- Exploration monster starting live cap: `round(1 + rooms / 4 + difficulty / 15)`, multiplied by multiplayer scaling and clamped to `2..36`.
- Exploration monster cap-extension interval: `60s` at difficulty 10 or lower, linearly down to `20s` at difficulty 80, then capped at `20s` before multiplayer/modifier pacing.
- Selected run difficulty multipliers scale scenario floor difficulty, trapped-room count, hole count, sculk patch count, and coin quota before floor modifiers are applied.

## Table

| Difficulty | Boss cooldown | Good modifier chance | Monster cap-extension base |
| --- | ---: | ---: | ---: |
| 10 | 0.55x | 40% | 60 s |
| 12 | 0.58x | 38% | 59 s |
| 14 | 0.61x | 36% | 58 s |
| 16 | 0.64x | 34% | 57 s |
| 18 | 0.67x | 32% | 55 s |
| 20 | 0.70x | 30% | 54 s |
| 22 | 0.73x | 28% | 53 s |
| 24 | 0.76x | 26% | 52 s |
| 26 | 0.79x | 24% | 51 s |
| 28 | 0.82x | 22% | 50 s |
| 30 | 0.85x | 20% | 49 s |
| 32 | 0.88x | 18% | 47 s |
| 34 | 0.91x | 16% | 46 s |
| 36 | 0.94x | 14% | 45 s |
| 38 | 0.97x | 12% | 44 s |
| 40 | 1.00x | 10% | 43 s |
| 44 | 1.03x | 6% | 41 s |
| 48 | 1.06x | 2% | 38 s |
| 52 | 1.09x | 0% | 36 s |
| 56 | 1.12x | 0% | 34 s |
| 60 | 1.15x | 0% | 31 s |
| 64 | 1.18x | 0% | 29 s |
| 68 | 1.21x | 0% | 27 s |
| 72 | 1.24x | 0% | 25 s |
| 76 | 1.27x | 0% | 22 s |
| 80 | 1.30x | 0% | 20 s |
| 84 | 1.33x | 0% | 20 s |
| 88 | 1.36x | 0% | 20 s |
| 92 | 1.39x | 0% | 20 s |
| 96 | 1.42x | 0% | 20 s |
| 100 | 1.45x | 0% | 20 s |
| 104 | 1.48x | 0% | 20 s |
| 108 | 1.51x | 0% | 20 s |
| 112 | 1.54x | 0% | 20 s |
| 116 | 1.57x | 0% | 20 s |
| 120 | 1.60x | 0% | 20 s |
| 124 | 1.63x | 0% | 20 s |
| 128 | 1.66x | 0% | 20 s |
| 132 | 1.69x | 0% | 20 s |
| 136 | 1.72x | 0% | 20 s |
| 140 | 1.75x | 0% | 20 s |
| 144 | 1.78x | 0% | 20 s |
| 148 | 1.81x | 0% | 20 s |
| 152 | 1.84x | 0% | 20 s |
| 156 | 1.87x | 0% | 20 s |
| 160 | 1.90x | 0% | 20 s |
| 164 | 1.93x | 0% | 20 s |
| 168 | 1.96x | 0% | 20 s |
| 172 | 1.99x | 0% | 20 s |
| 176 | 2.02x | 0% | 20 s |
| 180 | 2.05x | 0% | 20 s |
| 184 | 2.08x | 0% | 20 s |
| 188 | 2.11x | 0% | 20 s |
| 192 | 2.14x | 0% | 20 s |
| 196 | 2.17x | 0% | 20 s |
| 200 | 2.20x | 0% | 20 s |
