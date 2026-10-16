# Marbles OP AH Abilities
Paper 1.21.11 plugin: 10 elements, each with 2 actives + a passive, driven by **energy**.

- **F** = ability 1 (needs 3 energy) · **Shift+F** = ability 2 (needs 5 energy) · passive works at 0+ energy
- Start at 3 energy. Kill a player: +1. Die: -1. Reach **-5** and you're **deathbanned**.
- `/withdraw [n]` turns energy into **Energy Marbles** (right-click to redeem).
- **Element Reroller** item / `/reroll` / `/marbles reroll [player]` rerolls your element.
- **Marble Trader** item: right-click to open a trade menu (trades are in `config.yml`).
- Ability damage is *true damage* (ignores Prot 3 armor), capped per activation, and **respects totems**.
- HUD: icons above the hotbar with the cooldown underneath (needs the resource pack).

## Build
`mvn package` -> `target/MarblesOPAHAbilities.jar` (Java 21). Pushing to GitHub also builds the jar + pack via Actions.

## Install
1. Drop the jar in `plugins/` (Paper 1.21.11).
2. Zip the `resourcepack/` folder contents (the Action does this) and host it (e.g. a GitHub Release link),
   then set `resource-pack=` and `resource-pack-sha1=` in `server.properties`. If the client says the pack is
   incompatible, edit `pack_format` in `resourcepack/pack.mcmeta`.
3. Edit `plugins/MarblesOPAHAbilities/config.yml` (every damage/duration/cooldown/effect/trade) and `/marbles reload`.

## Commands (`marbles.admin`)
`/marbles element <e> [player]` · `energy <set|add> <n> [player]` · `give <energy|reroller|trader> [n] [player]` ·
`reroll [player]` · `revive <player>` · `reload`

Regenerate the pack art from `tools/art/*.jpeg` with `python3 tools/gen_pack.py` (Pillow, numpy, scipy).
