# Diamond Strength (Fabric 1.21.11)

Build: `./gradlew build` (JDK 21; run `gradle wrapper` first if there is no wrapper). Jar: build/libs/.

PROGRESSION
- /deposit: takes 20 diamonds and gives you a Diamond Strength Bottle item.
  Right-click the bottle to drink it: +0.5 strength. 6 bottles = 3.0 strength (max).
  At max strength bottles do nothing (not consumed) and /deposit refuses.
- At max strength, each player kill = 1 Diamond Heart (max 5).
- Every player is randomly given ONE ability on first join. It unlocks (level 1) on the first kill after 5 hearts,
  then each further kill upgrades it (level 2, level 3).
- Death: before heart level -0.5 strength; after -> lose 1 heart only. Ability level is never lost.
- Diamond ore ignores Fortune (Silk Touch still works). Big + buried diamond veins no longer generate
  (new chunks only).

ABILITIES (3 levels each). Press R (rebind in Controls > Diamond Strength) or /ability use.
- dash       teleport-dash 3 / 6 / 10 blocks
- pull       yank the target you look at (range 10/15/20)
- pulse      regeneration + absorption
- shockwave  AoE damage + knockback
- bolt       lightning where you look
- stun (passive)      60% chance to freeze enemy on hit (1.5 / 2.5 / 3.5 s)
- lifesteal (passive) heal 10/20/30% of damage dealt
- fortify (passive)   +2/4/6 armor

Commands: /deposit, /ability info, /ability use
All numbers are constants at the top of DiamondStrength.java.

IMPORTANT: open gradle.properties and replace yarn_mappings / loader_version / fabric_version / (Loom version in build.gradle)
with the exact current values for 1.21.11 from https://fabricmc.net/develop

BUILD WITHOUT INSTALLING ANYTHING (GitHub Actions)
1. Make a free GitHub account and a new repository.
2. Upload ALL files from this zip (including the hidden .github folder; on Mac press Cmd+Shift+. to see it).
3. Open the Actions tab -> "build" -> wait for the green check.
4. Click the finished run -> download "diamondstrength-jar" (a zip containing the .jar). Use the jar WITHOUT "-sources".
