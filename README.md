# Neon Relics — Fabric mod for Minecraft Java 26.3

The first time you join a world you are given one of seven relics at random: Inferno, Prosperity, Weather, Beast, Cyber, Bloodlusted or Haste.
Your relic, cooldowns, mastery and Cyber implant are saved with your player and kept when you die.

## Install (to play)
1. Install **Fabric Loader 0.20.1+** for **26.3** (https://fabricmc.net/use/installer/).
2. Put **Fabric API 0.165.2+26.3** (or newer) and `neon-relics-1.0.0.jar` in your `mods` folder.
3. Install the mod on the server too (and on every client for the HUD and the B key).

## Build the jar
You need **JDK 25**.
- **IntelliJ IDEA:** File → Open → this folder. Wait for Gradle to sync, then run the Gradle task `build`.
- **Command line:** `gradlew build` (Windows) or `./gradlew build` (Mac/Linux).

The jar ends up in `build/libs/neon-relics-1.0.0.jar`. Run the `runClient` Gradle task to test it in a dev client.

If you push this folder to GitHub, the included workflow (`.github/workflows/build.yml`) builds the jar automatically under **Actions → Artifacts**.

## Controls & commands
| | |
|---|---|
| **B** | Use your relic ability (rebind under Controls → Neon Relics) |
| `/relic` | Your relic's info, cooldown and mastery |
| `/relic ability` | Same as pressing B |
| `/relic weather clear\|rain\|thunder` | Weather relic (the B key shows clickable buttons) |
| `/relic implant boot_accelerator\|chrome_body` | Cyber relic (NOVA shows clickable buttons) |
| `/relic set <player> <relic>` | Op: give a relic |
| `/relic reroll <player>` | Op: random new relic |
| `/relic master <player>` | Op: instantly master (Inferno / Weather) |
| `/relic reset <player>` | Op: clear cooldowns, neural load and Cyber Maniac |

## Relics
**Inferno.** Passive: critical hits burn for 2 seconds (2 fire-damage ticks). The burn never stacks, it only refreshes. Ability: a 5-block-wide fire slash that deals 3 true damage and burns for 4 seconds (40s cooldown). Mastery: kill 3 players within 10s of slashing them. After mastery the slash deals 4 true damage, every burn lasts twice as long, and Fire Resistance no longer protects targets.

**Prosperity.** Villager trades cost half, and each trade has a 25% chance of paying out double. Evokers you kill drop 2 totems. Winning a raid has a 10% chance to upgrade your Hero of the Village to V. No ability, no mastery.

**Weather.** In rain, tridents deal +20% and count as Sharpness III. In thunderstorms they deal +35%, count as Sharpness V, and strike Channeling lightning on every hit, thrown or melee. The lightning only hits the target, not you. Ability: set the weather to clear, rain or thunder for 5 minutes (30 min cooldown). Mastery: summon 3 thunderstorms and kill at least one player during each; the cooldown then drops to 20 minutes.

**Beast.** After a spear hit, your next hit is a Claw (+3.5 true damage). Your next spear swing or right-click becomes an empowered lunge. If you're looking at an enemy within 24 blocks, you leap straight to it. If you're not, you dash 6 blocks forward. Ability: Bite (3 min cooldown), dealing 6 true damage and stunning the target for 2s. No mastery.

**Cyber.** Hit the same target 3 times in a row and it gets Slowness III for 3s. Get hit 3 times in a row and your attackers get Slowness V and Mining Fatigue V for 5s, while you get Speed III for 5s. These share a 1 min cooldown. Your AI, **NOVA**, sells one permanent implant for 10 diamonds:
- **Boot Accelerator.** Ability: Overdrive. For 8s, everything within 20 blocks is slowed and projectiles freeze, while you get Speed III and leave neon afterimages (20s cooldown). Each use adds 30% to the Neural Load bar on the right of your screen, and it drains slowly. At 100% you go **Cyber Maniac**: heavily buffed for 3 minutes, then you die.
- **Chrome Body.** No ability. You get permanent Resistance II, double mace damage, no fall damage, and permanent Cyber Maniac at level II (200%). You die after 1 hour unless you keep killing; any player or hostile-mob kill resets the clock.

**Bloodlusted.** Every kill gives Strength IV for 30s. Swords deal 1.5x damage. Ability: Strength IV and Speed III for 1 minute (3 min cooldown).

**Haste.** Permanent Speed II and Haste III. Ability: Overclock for 20s, with 2x attack speed, 1.25x damage and Speed III (3 min cooldown).

## Tweaking
Every number (cooldowns, durations, damage, implant price, chances) is in `src/main/java/com/neonrelics/Tuning.java`.

## Notes for building
This was written against the official 26.3 Fabric template (Loom 1.19, Loader 0.20.1, Fabric API 0.165.2+26.3). Names were checked against Fabric API's 26.3 source and other 26.3 mods. It could not be compiled in the environment it was written in. If Gradle reports a compile error, it will almost certainly be a single renamed Minecraft method or field. The likeliest ones are `MerchantOffer.addToSpecialPriceDiff`, and `Attributes.JUMP_STRENGTH`. Paste the error and it's a one-line fix.
